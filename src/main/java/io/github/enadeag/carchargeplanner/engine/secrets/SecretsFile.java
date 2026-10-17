package io.github.enadeag.carchargeplanner.engine.secrets;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.store.FileFormat;
import io.github.enadeag.carchargeplanner.engine.store.FileVersion;
import io.github.enadeag.carchargeplanner.engine.store.FormatProblem;
import io.github.enadeag.carchargeplanner.engine.store.Stored;
import io.github.enadeag.carchargeplanner.engine.store.StoredFile;
import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The keys in a JSON file beside the config, for when there is no usable keychain:
 * {@code {"version": 1, "open-meteo": {"api_key": "..."}}}, where a file without a version is version 1. Where files
 * have modes (Linux, macOS) it is readable and writable by its owner only; on Windows the user's own application data
 * folder already keeps it from other users. A file that cannot be used is kept as {@code secrets.json.damaged-<time>},
 * as private as it was, before new keys are written; no message quotes it.
 */
public final class SecretsFile {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private final StoredFile<Secrets> file;

    /**
     * Makes the file's reader and writer; nothing is read or written yet.
     *
     * @param path where the file is, or will be made; its folder is made when needed
     */
    public SecretsFile(Path path) {
        this.file = new StoredFile<>(path, new Format());
    }

    /**
     * Where the file is, to tell the owner.
     *
     * @return the path this was made with
     */
    public Path path() {
        return file.path();
    }

    /**
     * Whether there is a file.
     *
     * @return true when the file exists
     */
    boolean exists() {
        return Files.exists(path());
    }

    /**
     * Reads the file, first making it private when others could read it. It is read again only when it changed on
     * disk.
     *
     * @return the secrets in it; none when there is no file
     * @throws SecretsProblem when it cannot be read or made private, is not JSON, was written by a newer version of the
     *     app, or does not hold texts per provider; a provider's name in it must be a provider id
     */
    Secrets read() throws SecretsProblem {
        makePrivate();
        return switch (file.read()) {
            case Stored.Found<Secrets> found -> found.value();
            case Stored.Absent<Secrets> absent -> Secrets.NONE;
            case Stored.Unusable<Secrets> unusable when unusable.reason() == Stored.Reason.UNREADABLE ->
                    throw new SecretsProblem("Could not read " + path() + " (" + unusable.detail() + ")");
            case Stored.Unusable<Secrets> unusable -> throw new SecretsProblem(path() + ": " + unusable.detail()
                    + ". Fix it or delete it and enter the keys again.");
        };
    }

    /**
     * Writes the file: atomically, and made private, so there is no moment when others can read it.
     *
     * @param secrets the secrets to keep; what is left out is forgotten
     * @throws SecretsProblem when a name has characters that are not allowed or the file could not be written; the
     *     old file then stays as it was
     */
    void write(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        try {
            file.write(secrets);
        } catch (IOException e) {
            throw new SecretsProblem("Could not write " + path() + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * The values are safe in the keychain when this is called; a file that stays is moved again next time. A file
     * that could not be read is kept aside instead, as its keys are not in the keychain.
     */
    void delete() {
        try {
            file.delete();
        } catch (IOException e) {
            // the file is moved into the keychain again next time
        }
    }

    /**
     * Corrects a file that others can read, where files have modes.
     *
     * @return true when its mode had to be corrected
     * @throws SecretsProblem when others can read it and its mode cannot be changed
     */
    boolean makePrivate() throws SecretsProblem {
        if (!hasFileModes()) {
            return false;
        }
        try {
            if (Files.getPosixFilePermissions(path()).equals(OWNER_ONLY)) {
                return false;
            }
            Files.setPosixFilePermissions(path(), OWNER_ONLY);
            return true;
        } catch (IOException e) {
            throw new SecretsProblem(path() + " is readable by other users and could not be made private ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    private boolean hasFileModes() {
        return path().getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** The JSON shape of the keys: an object per provider, of texts only. */
    private static final class Format implements FileFormat<Secrets> {

        @Override
        public Secrets read(JsonNode root) throws FormatProblem {
            var byProvider = new HashMap<ProviderId, Map<String, String>>();
            for (var provider : VERSION.upToDate(root).properties()) {
                if (!provider.getValue().isObject()) {
                    throw new FormatProblem("\"" + provider.getKey() + "\" must hold texts only");
                }
                if (!ProviderId.isValid(provider.getKey())) {
                    throw new FormatProblem("\"" + provider.getKey() + "\" is not a provider id");
                }
                var values = new HashMap<String, String>();
                for (var setting : provider.getValue().properties()) {
                    if (!setting.getValue().isString()) {
                        throw new FormatProblem("\"" + provider.getKey() + "\" must hold texts only");
                    }
                    values.put(setting.getKey(), setting.getValue().stringValue());
                }
                byProvider.put(new ProviderId(provider.getKey()), values);
            }
            return new Secrets(byProvider);
        }

        @Override
        public ObjectNode write(Secrets secrets) {
            var sorted = new TreeMap<String, Map<String, String>>();
            secrets.byProvider().forEach((id, values) -> sorted.put(id.value(), new TreeMap<>(values)));
            var body = StoredJson.object();
            sorted.forEach((id, values) -> {
                var provider = body.putObject(id);
                values.forEach(provider::put);
            });
            return VERSION.stamped(body);
        }

        @Override
        public boolean holdsSecrets() {
            return true;
        }
    }
}
