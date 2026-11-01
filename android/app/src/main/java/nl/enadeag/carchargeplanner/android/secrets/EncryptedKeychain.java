package nl.enadeag.carchargeplanner.android.secrets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
 * The Android app's {@link Keychain}: every value encrypted with an AES key ({@link KeychainKeys}, on the phone in the
 * Android Keystore), all entries in one file. Each value is encrypted with AES-256-GCM under a random 12-byte IV that
 * the cipher picks, with the entry's name as additional authenticated data, so an entry moved to another name fails
 * its check. The file, {@code {"version": 1, "key": "<alias>", "entries": {"<name>": {"key": "<alias>", "value":
 * "<Base64 of IV and ciphertext>"}}}}, names the key that new values are encrypted with and, for each entry, the key it
 * was encrypted with. It is written whole and atomically through {@link PrivateFiles}, and lives in the app's folder
 * that is never backed up.
 *
 * <p>The store never removes a key or an entry it did not delete on request. The first key is made at the first write,
 * under the first alias. When the key in use is lost for good, or still missing after {@value #ABSENCE_CHECKS} looks
 * within about a second while entries need it, the next write makes a new key under a new alias ({@code <first>-2},
 * {@code -3} and so on, never one that holds a key), and the entries of the old key stay: readable again should that
 * key come back, otherwise refused, until their names are written again.
 *
 * <p>The store has no locked state: it never throws {@code LOCKED}. As the engine runs it without the fallback to
 * {@code secrets.json}, a {@code REFUSED} read of the {@code index} makes a save start a new index; so this store
 * refuses only what is lost for good, and every failure that may pass is {@code UNAVAILABLE}:
 * <ul>
 *   <li>{@code REFUSED}: the entry's key missing after every look, or lost for good ({@link KeychainKeys#lostForGood});
 *       an entry that fails its check ({@link AEADBadTagException}: changed, moved to another name, or encrypted with
 *       another key); an entry that is not this format (an alias not of this store's form included) or does not
 *       decrypt to UTF-8; a file that is not this format (not UTF-8, not JSON, no whole version from 1, no alias of
 *       this store's form, no entries), on a read or a delete; a file written by a
 *       newer version of the app; a value that UTF-8 cannot hold, such as a lone surrogate.</li>
 *   <li>{@code UNAVAILABLE}: the file could not be read, written or moved aside ({@link IOException}); a key could
 *       not be got, made or used for a reason that may pass (any other {@link GeneralSecurityException}, such as a
 *       busy Keystore); an interrupt while looking again for a missing key; and any runtime exception, such as
 *       Android's {@code ProviderException} from the Keystore. Nothing is changed then.</li>
 * </ul>
 * A write over a file that is not this format first moves it aside to {@value #DAMAGED_FILE_NAME} in the same
 * folder, in one step where the file system can, replacing an older one there, and logs that with the paths only; a
 * failure to move it is {@code UNAVAILABLE} and leaves it in place, while a failure to write the new file after it
 * leaves it moved. A file of a newer version is never overwritten or moved. Neither a
 * value nor a key ever appears in a message or the log. Safe to use from several threads, also through several
 * instances on one file: one call at a time per file. A call may block for about a second while it looks again for a
 * missing key, so it belongs off the main thread.
 */
public final class EncryptedKeychain implements Keychain {

    /** The file's name; the app's data extraction rules name it too. */
    public static final String FILE_NAME = "keychain.json";

    /** Where a file that is not this format goes before a write replaces it; the data extraction rules name it too. */
    public static final String DAMAGED_FILE_NAME = FILE_NAME + ".damaged";

    /** The version of the file's shape that this app writes. */
    static final int VERSION = 1;

    /** How often a key is looked for before it counts as missing, while entries need it. */
    static final int ABSENCE_CHECKS = 3;

    /** How many aliases after the one in use are tried for a new key. */
    static final int MAX_ALIASES = 1000;

    private static final long ABSENCE_PAUSE_MILLIS = 500;
    private static final Logger LOG = LoggerFactory.getLogger(EncryptedKeychain.class);
    /** One lock per file, so that two stores on one file never write it at once. */
    private static final ConcurrentMap<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String VERSION_FIELD = "version";
    private static final String KEY_FIELD = "key";
    private static final String ENTRIES_FIELD = "entries";
    private static final String VALUE_FIELD = "value";

    /** Waits between two looks for a missing key. */
    interface Pause {

        /**
         * Waits.
         *
         * @param millis how long, in milliseconds
         * @throws InterruptedException when the thread is interrupted while waiting
         */
        void pause(long millis) throws InterruptedException;
    }

    private final Path file;
    private final PrivateFiles files;
    private final KeychainKeys keys;
    private final String firstAlias;
    private final Pause pause;
    private final Object lock;

    /**
     * The entries as the file holds them, the alias of the key for new values, or why the file is not this format.
     *
     * @param entries per name, the alias and the encrypted value
     * @param alias the alias of the key that new values are encrypted with
     * @param damage why the file is not this format; null when it is
     */
    private record Contents(ObjectNode entries, String alias, String damage) {
    }

    /**
     * The outcome of looking for a key.
     *
     * @param key the key; null when there is none
     * @param lost whether the key is lost for good, rather than not there
     */
    private record Lookup(SecretKey key, boolean lost) {
    }

    /**
     * Makes the store; nothing is read, written or made yet.
     *
     * @param file the file that keeps the entries, {@link #FILE_NAME} in the app's folder that is never backed up; it
     *     and its folder are made at the first write
     * @param files how the file is written atomically; never null
     * @param keys the keys the values are encrypted with; never null
     * @param firstAlias the alias of the first key, which later ones extend with {@code -2}, {@code -3} and so on;
     *     {@link AndroidKeystoreKeys#ALIAS} for the app
     */
    public EncryptedKeychain(Path file, PrivateFiles files, KeychainKeys keys, String firstAlias) {
        this(file, files, keys, firstAlias, Thread::sleep);
    }

    /**
     * Makes the store with a pause of its own between two looks for a missing key.
     *
     * @param file the file that keeps the entries
     * @param files how the file is written atomically
     * @param keys the keys the values are encrypted with
     * @param firstAlias the alias of the first key
     * @param pause waits between two looks for a missing key
     */
    EncryptedKeychain(Path file, PrivateFiles files, KeychainKeys keys, String firstAlias, Pause pause) {
        this.file = file;
        this.files = files;
        this.keys = keys;
        this.firstAlias = firstAlias;
        this.pause = pause;
        this.lock = LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), path -> new Object());
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        Objects.requireNonNull(name, "name");
        synchronized (lock) {
            try {
                var contents = readFile();
                if (contents.damage() != null) {
                    throw refused(file + " cannot be read: " + contents.damage());
                }
                var stored = contents.entries().get(name);
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
                var damaged = contents.damage() != null;
                if (damaged) {
                    contents = new Contents(StoredJson.object(), firstAlias, null);
                }
                var entry = seal(name, plain, contents);
                if (damaged) {
                    setAside();
                }
                contents.entries().set(name, entry);
                save(contents.entries(), entry.get(KEY_FIELD).stringValue());
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
                if (contents.damage() != null) {
                    throw refused(file + " cannot be read: " + contents.damage());
                }
                if (contents.entries().remove(name) != null) {
                    save(contents.entries(), contents.alias());
                }
            } catch (RuntimeException e) {
                throw failed(e);
            }
        }
    }

    /** Moves a file that is not this format to {@link #DAMAGED_FILE_NAME}, replacing an older one there. */
    private void setAside() throws KeychainException {
        var aside = file.resolveSibling(DAMAGED_FILE_NAME);
        try {
            try {
                Files.move(file, aside, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw unavailable("could not move " + file + ", which cannot be read, aside to " + aside, e);
        }
        LOG.warn(file + " cannot be read: moved aside to " + aside + ", and a new one replaces it");
    }

    private Contents readFile() throws KeychainException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return new Contents(StoredJson.object(), firstAlias, null);
        } catch (IOException e) {
            throw unavailable("could not read " + file, e);
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
        var alias = object.get(KEY_FIELD);
        if (alias == null || !alias.isString() || number(alias.stringValue()) == 0) {
            return damaged("it names no key of this keychain");
        }
        if (!(object.get(ENTRIES_FIELD) instanceof ObjectNode entries)) {
            return damaged("it has no entries");
        }
        return new Contents(entries, alias.stringValue(), null);
    }

    private Contents damaged(String why) {
        return new Contents(StoredJson.object(), firstAlias, why);
    }

    private void save(ObjectNode entries, String alias) throws KeychainException {
        var root = StoredJson.object().put(VERSION_FIELD, VERSION).put(KEY_FIELD, alias);
        root.set(ENTRIES_FIELD, entries);
        try {
            files.write(file, StoredJson.compact(root));
        } catch (IOException e) {
            throw unavailable("could not write " + file, e);
        }
    }

    private String open(String name, JsonNode stored) throws KeychainException {
        var alias = stored.get(KEY_FIELD);
        var value = stored.get(VALUE_FIELD);
        byte[] bytes = null;
        if (alias != null && alias.isString() && number(alias.stringValue()) != 0 && value != null
                && value.isString()) {
            try {
                bytes = Base64.getDecoder().decode(value.stringValue());
            } catch (IllegalArgumentException ignored) {
                // refused below, as any other entry that is not this format
            }
        }
        if (bytes == null || bytes.length < IV_BYTES + TAG_BITS / Byte.SIZE) {
            throw refused("the entry " + name + " is not an encrypted value");
        }
        var found = find(alias.stringValue(), true);
        if (found.key() == null) {
            throw refused("the key of the entry " + name + (found.lost() ? " is lost for good" : " is gone"));
        }
        try {
            var cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, found.key(), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            var plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString();
        } catch (AEADBadTagException e) {
            throw refused("the entry " + name + " fails its check: it was changed, or encrypted with another key", e);
        } catch (CharacterCodingException e) {
            throw refused("the entry " + name + " does not decrypt to text", e);
        } catch (GeneralSecurityException e) {
            if (keys.lostForGood(e)) {
                throw refused("could not decrypt the entry " + name + ": its key is lost for good", e);
            }
            throw unavailable("could not decrypt the entry " + name, e);
        }
    }

    /**
     * Looks for a key; when it is not there and {@code confirm} is set, looks again after a pause, so a Keystore that
     * is restarting is not taken for one that lost the key.
     */
    private Lookup find(String alias, boolean confirm) throws KeychainException {
        for (int look = 1; ; look++) {
            Optional<SecretKey> found;
            try {
                found = keys.find(alias);
            } catch (GeneralSecurityException e) {
                if (keys.lostForGood(e)) {
                    return new Lookup(null, true);
                }
                throw unavailable("could not get the key " + alias, e);
            } catch (IOException e) {
                throw unavailable("could not get the key " + alias, e);
            }
            if (found.isPresent()) {
                return new Lookup(found.get(), false);
            }
            if (!confirm || look >= ABSENCE_CHECKS) {
                return new Lookup(null, false);
            }
            try {
                pause.pause(ABSENCE_PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw unavailable("interrupted while looking for the key " + alias, e);
            }
        }
    }

    /**
     * The entry: the value encrypted under the key in use; under a new key when that one is lost for good, or missing
     * while entries need it. At most two keys are tried.
     */
    private ObjectNode seal(String name, byte[] plain, Contents contents) throws KeychainException {
        var alias = contents.alias();
        var needed = uses(contents.entries(), alias);
        var found = find(alias, needed);
        if (found.key() == null && !found.lost() && !needed) {
            return sealed(name, plain, alias, make(alias));
        }
        if (found.key() != null) {
            try {
                return entry(alias, sealWith(found.key(), name, plain));
            } catch (GeneralSecurityException e) {
                if (!keys.lostForGood(e)) {
                    throw unavailable("could not encrypt the entry " + name, e);
                }
            }
        }
        LOG.warn("the keychain's key " + alias + " is lost or gone: a new key encrypts the values from now on; the "
                + "entries of the old one are kept");
        var renewed = newAlias(contents);
        return sealed(name, plain, renewed, make(renewed));
    }

    private static ObjectNode sealed(String name, byte[] plain, String alias, SecretKey key) throws KeychainException {
        try {
            return entry(alias, sealWith(key, name, plain));
        } catch (GeneralSecurityException e) {
            throw unavailable("could not encrypt the entry " + name + " with the new key " + alias, e);
        }
    }

    private SecretKey make(String alias) throws KeychainException {
        try {
            var made = keys.make(alias);
            LOG.info("made the keychain's key " + alias);
            return made;
        } catch (GeneralSecurityException | IOException e) {
            throw unavailable("could not make the key " + alias, e);
        }
    }

    /** The next alias after the one in use that no entry names and that holds no key, so no key is ever replaced. */
    private String newAlias(Contents contents) throws KeychainException {
        var current = contents.alias();
        int number = number(current);
        for (int next = number + 1; next <= number + MAX_ALIASES; next++) {
            var candidate = firstAlias + "-" + next;
            var found = find(candidate, false);
            if (!uses(contents.entries(), candidate) && found.key() == null && !found.lost()) {
                return candidate;
            }
        }
        throw unavailable("found no free alias for a new key after " + current, null);
    }

    /**
     * The number of one of this store's aliases: 1 for the first, n for {@code <first>-n}, from 2 up to a million;
     * 0 for an alias of another form.
     */
    private int number(String alias) {
        if (alias.equals(firstAlias)) {
            return 1;
        }
        var prefix = firstAlias + "-";
        if (!alias.startsWith(prefix) || !alias.substring(prefix.length()).matches("[1-9][0-9]{0,5}")) {
            return 0;
        }
        var number = Integer.parseInt(alias.substring(prefix.length()));
        return number >= 2 ? number : 0;
    }

    private static boolean uses(ObjectNode entries, String alias) {
        for (var entry : entries.values()) {
            var key = entry.get(KEY_FIELD);
            if (key != null && key.isString() && key.stringValue().equals(alias)) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode entry(String alias, String sealed) {
        return StoredJson.object().put(KEY_FIELD, alias).put(VALUE_FIELD, sealed);
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
            throw refused("the value is not text that UTF-8 can hold, such as one with a lone surrogate", e);
        }
    }

    private static KeychainException failed(RuntimeException e) {
        return unavailable("the keychain failed", e);
    }

    private static KeychainException refused(String message) {
        return new KeychainException(KeychainException.Kind.REFUSED, message);
    }

    private static KeychainException refused(String message, Throwable cause) {
        return new KeychainException(KeychainException.Kind.REFUSED, message, cause);
    }

    /** The message names the cause's class, never its message, which may quote what it read. */
    private static KeychainException unavailable(String message, Exception cause) {
        if (cause == null) {
            return new KeychainException(KeychainException.Kind.UNAVAILABLE, message);
        }
        return new KeychainException(KeychainException.Kind.UNAVAILABLE,
                message + " (" + cause.getClass().getSimpleName() + ")", cause);
    }
}
