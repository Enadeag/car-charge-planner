package io.github.enadeag.carchargeplanner.engine.secrets;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The keys in a JSON file beside the config, for when there is no usable keychain:
 * {@code {"open-meteo": {"api_key": "..."}}}. Where files have modes (Linux, macOS) it is readable and writable by its
 * owner only; on Windows the user's own application data folder already keeps it from other users.
 */
public final class SecretsFile {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private final Path path;
    private final JsonMapper json = new JsonMapper();

    /**
     * Makes the file's reader and writer; nothing is read or written yet.
     *
     * @param path where the file is, or will be made; its folder is made when needed
     */
    public SecretsFile(Path path) {
        this.path = path;
    }

    /**
     * Where the file is, to tell the owner.
     *
     * @return the path this was made with
     */
    public Path path() {
        return path;
    }

    /**
     * Whether there is a file.
     *
     * @return true when the file exists
     */
    boolean exists() {
        return Files.exists(path);
    }

    /**
     * Reads the file, first making it private when others could read it.
     *
     * @return the secrets in it
     * @throws SecretsProblem when it cannot be read or made private, is not JSON, or does not hold texts per provider;
     *     a provider's name in it must be a provider id
     */
    Secrets read() throws SecretsProblem {
        makePrivate();
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException e) {
            throw new SecretsProblem("Could not read " + path + " (" + e.getClass().getSimpleName() + ")");
        }
        try {
            return secrets(json.readTree(text));
        } catch (JacksonException e) {
            // its message would quote part of the file: say where, not what
            throw new SecretsProblem(path + " is not valid JSON. Fix it or delete it and enter the keys again.");
        }
    }

    private Secrets secrets(JsonNode root) throws SecretsProblem {
        if (!root.isObject()) {
            throw new SecretsProblem(path + " must hold the keys per provider");
        }
        var byProvider = new HashMap<ProviderId, Map<String, String>>();
        for (var provider : root.properties()) {
            if (!provider.getValue().isObject()) {
                throw new SecretsProblem(path + ": \"" + provider.getKey() + "\" must hold texts only");
            }
            if (!ProviderId.isValid(provider.getKey())) {
                throw new SecretsProblem(path + ": \"" + provider.getKey() + "\" is not a provider id");
            }
            var values = new HashMap<String, String>();
            for (var setting : provider.getValue().properties()) {
                if (!setting.getValue().isString()) {
                    throw new SecretsProblem(path + ": \"" + provider.getKey() + "\" must hold texts only");
                }
                values.put(setting.getKey(), setting.getValue().stringValue());
            }
            byProvider.put(new ProviderId(provider.getKey()), values);
        }
        return new Secrets(byProvider);
    }

    /**
     * Writes the file atomically, and created private: there is no moment when others can read it.
     *
     * @param secrets the secrets to keep; what is left out is forgotten
     * @throws SecretsProblem when a name has characters that are not allowed or the file could not be written; the
     *     old file then stays as it was
     */
    void write(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        var sorted = new TreeMap<String, Map<String, String>>();
        secrets.byProvider().forEach((id, values) -> sorted.put(id.value(), new TreeMap<>(values)));
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.deleteIfExists(temporary); // a leftover may have other permissions
            Files.writeString(Files.createFile(temporary, privateFile()),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(sorted), StandardOpenOption.WRITE);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw new SecretsProblem("Could not write " + path + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** The values are safe in the keychain when this is called; a file that stays is moved again next time. */
    void delete() {
        deleteQuietly(path);
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
            if (Files.getPosixFilePermissions(path).equals(OWNER_ONLY)) {
                return false;
            }
            Files.setPosixFilePermissions(path, OWNER_ONLY);
            return true;
        } catch (IOException e) {
            throw new SecretsProblem(path + " is readable by other users and could not be made private ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    private boolean hasFileModes() {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private FileAttribute<?>[] privateFile() {
        return hasFileModes() ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // nothing worth stopping for: see the callers
        }
    }
}
