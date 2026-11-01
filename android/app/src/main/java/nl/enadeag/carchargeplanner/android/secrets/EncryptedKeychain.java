package nl.enadeag.carchargeplanner.android.secrets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Android app's {@link Keychain}: every value encrypted with one AES key ({@link KeychainKey}, on the phone in the
 * Android Keystore), all entries in one file. Each value is encrypted with AES-256-GCM under a random 12-byte IV that
 * the cipher picks, with the entry's name as additional authenticated data, so an entry moved to another name fails
 * its check. The file, {@code {"version": 1, "entries": {"<name>": "<Base64 of IV and ciphertext>"}}}, is written
 * whole and atomically through {@link PrivateFiles}; it lives in the app's folder that is never backed up.
 *
 * <p>The store has no locked state: it never throws {@code LOCKED}. The key is made at the first write. As the engine
 * runs it without the fallback to {@code secrets.json}, a {@code REFUSED} read of the {@code index} makes a save start
 * a new index; so this store refuses only what is lost for good, and every failure that may pass is
 * {@code UNAVAILABLE}:
 * <ul>
 *   <li>{@code REFUSED}: the key is gone while the entry is there ({@link KeychainKey#find()} finds none), or lost for
 *       good ({@link KeychainKey#lostForGood}, such as a key the system invalidated); an entry that fails its check
 *       ({@link AEADBadTagException}: changed, moved to another name, or encrypted with another key); an entry that is
 *       not Base64 of an IV and a tag, or does not decrypt to UTF-8; a file that is not this format (not UTF-8, not
 *       JSON, no version or no entries), on a read or a delete; a file written by a newer version of the app; a value
 *       that UTF-8 cannot hold, such as a lone surrogate.</li>
 *   <li>{@code UNAVAILABLE}: the file could not be read or written ({@link IOException}); the key could not be got,
 *       made or used for a reason that may pass (any other {@link GeneralSecurityException}, such as a busy Keystore);
 *       and any runtime exception, such as Android's {@code ProviderException} from the Keystore.</li>
 * </ul>
 * A write that finds the key gone or lost for good removes every entry, as none can be read again, then makes a new
 * key; a write over a file that is not this format replaces it; a file of a newer version is never overwritten.
 * Neither a value nor a key ever appears in a message or the log. Safe to use from several threads: one call at a
 * time.
 */
public final class EncryptedKeychain implements Keychain {

    /** The file's name; the app's data extraction rules name it too. */
    public static final String FILE_NAME = "keychain.json";

    /** The version of the file's shape that this app writes. */
    static final int VERSION = 1;

    private static final Logger LOG = LoggerFactory.getLogger(EncryptedKeychain.class);
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION_FIELD = "version";
    private static final String ENTRIES_FIELD = "entries";

    private final Path file;
    private final PrivateFiles files;
    private final KeychainKey key;
    private final Object lock = new Object();

    /** The entries as the file holds them, or why the file is not this format. */
    private static final class Contents {

        private final ObjectNode entries;
        private final String damage;

        private Contents(ObjectNode entries, String damage) {
            this.entries = entries;
            this.damage = damage;
        }
    }

    /**
     * Makes the store; nothing is read, written or made yet.
     *
     * @param file the file that keeps the entries, {@link #FILE_NAME} in the app's folder that is never backed up; it
     *     and its folder are made at the first write
     * @param files how the file is written atomically; never null
     * @param key the key the values are encrypted with; never null
     */
    public EncryptedKeychain(Path file, PrivateFiles files, KeychainKey key) {
        this.file = file;
        this.files = files;
        this.key = key;
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        Objects.requireNonNull(name, "name");
        synchronized (lock) {
            try {
                var contents = readFile();
                if (contents.damage != null) {
                    throw refused(file + " cannot be read: " + contents.damage);
                }
                var stored = contents.entries.get(name);
                return stored == null ? Optional.empty() : Optional.of(open(name, stored));
            } catch (RuntimeException e) {
                throw failed(e);
            }
        }
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        var plain = utf8(value);
        synchronized (lock) {
            try {
                var contents = readFile();
                var entries = contents.entries;
                if (contents.damage != null) {
                    LOG.warn(file + " cannot be read (" + contents.damage + "): a new one replaces it");
                    entries = StoredJson.object();
                }
                var sealed = seal(name, plain, entries);
                entries.put(name, sealed);
                save(entries);
            } catch (RuntimeException e) {
                throw failed(e);
            }
        }
    }

    @Override
    public void delete(String name) throws KeychainException {
        Objects.requireNonNull(name, "name");
        synchronized (lock) {
            try {
                var contents = readFile();
                if (contents.damage != null) {
                    throw refused(file + " cannot be read: " + contents.damage);
                }
                if (contents.entries.remove(name) != null) {
                    save(contents.entries);
                }
            } catch (RuntimeException e) {
                throw failed(e);
            }
        }
    }

    private Contents readFile() throws KeychainException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return new Contents(StoredJson.object(), null);
        } catch (IOException e) {
            throw unavailable("could not read " + file + " (" + e.getClass().getSimpleName() + ")", e);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return damaged("it is not UTF-8 text");
        }
        JsonNode root;
        try {
            root = StoredJson.parse(text);
        } catch (JacksonException e) {
            return damaged("it is not JSON");
        }
        if (!(root instanceof ObjectNode object)) {
            return damaged("it is not one JSON object");
        }
        var version = object.get(VERSION_FIELD);
        if (version == null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 1) {
            return damaged("it has no file version from 1");
        }
        if (version.intValue() > VERSION) {
            throw refused(file + " was written by a newer version of the app (file version " + version.intValue()
                    + "; this version reads up to " + VERSION + ")");
        }
        if (!(object.get(ENTRIES_FIELD) instanceof ObjectNode entries)) {
            return damaged("it has no entries");
        }
        return new Contents(entries, null);
    }

    private static Contents damaged(String why) {
        return new Contents(StoredJson.object(), why);
    }

    private void save(ObjectNode entries) throws KeychainException {
        var root = StoredJson.object().put(VERSION_FIELD, VERSION);
        root.set(ENTRIES_FIELD, entries);
        try {
            files.write(file, StoredJson.compact(root));
        } catch (IOException e) {
            throw unavailable("could not write " + file + " (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private String open(String name, JsonNode stored) throws KeychainException {
        byte[] bytes = null;
        if (stored.isString()) {
            try {
                bytes = Base64.getDecoder().decode(stored.stringValue());
            } catch (IllegalArgumentException ignored) {
                // refused below, as any other entry that is not this format
            }
        }
        if (bytes == null || bytes.length < IV_BYTES + TAG_BITS / Byte.SIZE) {
            throw refused("the entry " + name + " is not an encrypted value");
        }
        var secretKey = keyToRead(name);
        try {
            var cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            var plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString();
        } catch (AEADBadTagException e) {
            throw new KeychainException(KeychainException.Kind.REFUSED, "the entry " + name
                    + " fails its check: it was changed, or encrypted with another key", e);
        } catch (CharacterCodingException e) {
            throw refused("the entry " + name + " does not decrypt to text");
        } catch (GeneralSecurityException e) {
            throw keyFailure("could not decrypt the entry " + name, e);
        }
    }

    private SecretKey keyToRead(String name) throws KeychainException {
        Optional<SecretKey> found;
        try {
            found = key.find();
        } catch (GeneralSecurityException e) {
            throw keyFailure("could not get the key to read " + name, e);
        } catch (IOException e) {
            throw unavailable("could not get the key to read " + name + " (" + e.getClass().getSimpleName() + ")", e);
        }
        if (found.isEmpty()) {
            throw refused("the key of the entry " + name + " is gone");
        }
        return found.get();
    }

    /** The value encrypted under the key, with a new key when the one there is gone or lost for good. */
    private String seal(String name, byte[] plain, ObjectNode entries) throws KeychainException {
        var secretKey = keyToWrite(entries);
        try {
            return sealWith(secretKey, name, plain);
        } catch (GeneralSecurityException e) {
            if (!key.lostForGood(e)) {
                throw unavailable("could not encrypt the entry " + name + " (" + e.getClass().getSimpleName() + ")",
                        e);
            }
        }
        try {
            return sealWith(renewed(entries, true), name, plain);
        } catch (GeneralSecurityException e) {
            throw unavailable("could not encrypt the entry " + name + " with a new key ("
                    + e.getClass().getSimpleName() + ")", e);
        }
    }

    private SecretKey keyToWrite(ObjectNode entries) throws KeychainException {
        Optional<SecretKey> found;
        boolean lost = false;
        try {
            found = key.find();
        } catch (GeneralSecurityException e) {
            if (!key.lostForGood(e)) {
                throw unavailable("could not get the key (" + e.getClass().getSimpleName() + ")", e);
            }
            found = Optional.empty();
            lost = true;
        } catch (IOException e) {
            throw unavailable("could not get the key (" + e.getClass().getSimpleName() + ")", e);
        }
        return found.isPresent() ? found.get() : renewed(entries, lost);
    }

    /**
     * A new key, after removing every entry, which only the key that is gone could read. The emptied file is written
     * first, so it never holds entries of the old key beside those of the new one.
     */
    private SecretKey renewed(ObjectNode entries, boolean forgetOld) throws KeychainException {
        if (!entries.isEmpty()) {
            var names = new ArrayList<>(entries.propertyNames());
            LOG.warn("the keychain's key is gone, so its " + names.size() + " entries can never be read again and are "
                    + "removed: " + names);
            entries.removeAll();
            save(entries);
        }
        try {
            if (forgetOld) {
                key.forget();
            }
            var made = key.make();
            LOG.info("made a new key for the keychain");
            return made;
        } catch (GeneralSecurityException | IOException e) {
            throw unavailable("could not make a key (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static String sealWith(SecretKey secretKey, String name, byte[] plain) throws GeneralSecurityException {
        var cipher = Cipher.getInstance(CIPHER);
        // no IV of our own: the Keystore refuses one for a key that requires randomized encryption, and both it and
        // the JVM pick a random one for GCM
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        var sealed = cipher.doFinal(plain);
        var iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) {
            throw new GeneralSecurityException("the cipher chose no IV of " + IV_BYTES + " bytes");
        }
        var bytes = new byte[IV_BYTES + sealed.length];
        System.arraycopy(iv, 0, bytes, 0, IV_BYTES);
        System.arraycopy(sealed, 0, bytes, IV_BYTES, sealed.length);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] utf8(String value) throws KeychainException {
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            var bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw refused("the value is not text that UTF-8 can hold, such as one with a lone surrogate");
        }
    }

    private KeychainException keyFailure(String what, GeneralSecurityException e) {
        if (key.lostForGood(e)) {
            return new KeychainException(KeychainException.Kind.REFUSED,
                    what + ": the key is lost for good (" + e.getClass().getSimpleName() + ")", e);
        }
        return unavailable(what + " (" + e.getClass().getSimpleName() + ")", e);
    }

    private static KeychainException failed(RuntimeException e) {
        return unavailable("the keychain failed (" + e.getClass().getSimpleName() + ")", e);
    }

    private static KeychainException refused(String message) {
        return new KeychainException(KeychainException.Kind.REFUSED, message);
    }

    private static KeychainException unavailable(String message, Exception cause) {
        return new KeychainException(KeychainException.Kind.UNAVAILABLE, message, cause);
    }
}
