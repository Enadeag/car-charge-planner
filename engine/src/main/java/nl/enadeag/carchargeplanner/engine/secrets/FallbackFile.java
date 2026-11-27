package nl.enadeag.carchargeplanner.engine.secrets;

import java.nio.file.Path;

/**
 * The private file as {@link ApiKeys}' fallback: where the keys are while it exists, as far as it is the fallback. With
 * {@link KeyFallback#NONE} it is never in use, so it is never read, deleted or moved by a load or a move.
 */
final class FallbackFile {

    private final SecretsFile file;
    private final KeyFallback fallback;

    FallbackFile(SecretsFile file, KeyFallback fallback) {
        this.file = file;
        this.fallback = fallback;
    }

    /** Whether the keys may be in the file: there is one, and it is the fallback. */
    boolean inUse() {
        return isTheFallback() && file.exists();
    }

    boolean isTheFallback() {
        return fallback == KeyFallback.PRIVATE_FILE;
    }

    Path path() {
        return file.path();
    }

    /**
     * @return the keys in it
     * @throws SecretsProblem when it cannot be read, or holds a name that is not allowed
     */
    Secrets read() throws SecretsProblem {
        var keys = file.read();
        keys.checkNames();
        return keys;
    }

    /**
     * Writes the file, applying the edit to what it holds; a file that cannot be read counts as none, and is set aside
     * as it is replaced.
     */
    void write(KeychainWithFallback.Edit edit) throws SecretsProblem {
        var stored = Secrets.NONE;
        if (!edit.replaces() && file.exists()) {
            try {
                stored = file.read();
            } catch (SecretsProblem ignored) {
                // set aside by the write
            }
        }
        file.write(edit.apply(stored));
    }

    void delete() {
        file.delete();
    }
}
