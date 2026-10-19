package nl.enadeag.carchargeplanner.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The keychain on Windows: the Credential Manager, as generic credentials of this user on this computer, reached
 * through {@link Advapi32}.
 *
 * <p>Over ssh, without an interactive logon session, Windows refuses with error 1312, which counts as no keychain
 * ({@link KeychainException.Kind#UNAVAILABLE}); the engine then keeps the keys in its private file. Other errors are
 * refusals. The Credential Manager is never locked while its user is logged on, so it has no unlocking.
 */
public final class CredentialManagerKeychain implements Keychain {

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;
    private static final int ERROR_NO_SUCH_LOGON_SESSION = 1312;

    /** CREDENTIALW from wincred.h, as laid out on 64-bit Windows. */
    private static final StructLayout CREDENTIAL = MemoryLayout.structLayout(
            JAVA_INT.withName("Flags"),
            JAVA_INT.withName("Type"),
            ADDRESS.withName("TargetName"),
            ADDRESS.withName("Comment"),
            MemoryLayout.sequenceLayout(2, JAVA_INT).withName("LastWritten"),
            JAVA_INT.withName("CredentialBlobSize"),
            MemoryLayout.paddingLayout(4),
            ADDRESS.withName("CredentialBlob"),
            JAVA_INT.withName("Persist"),
            JAVA_INT.withName("AttributeCount"),
            ADDRESS.withName("Attributes"),
            ADDRESS.withName("TargetAlias"),
            ADDRESS.withName("UserName"));
    private static final VarHandle TYPE = field("Type");
    private static final VarHandle TARGET_NAME = field("TargetName");
    private static final VarHandle BLOB_SIZE = field("CredentialBlobSize");
    private static final VarHandle BLOB = field("CredentialBlob");
    private static final VarHandle PERSIST = field("Persist");
    private static final VarHandle USER_NAME = field("UserName");
    private static final VarHandle GET_LAST_ERROR = Linker.Option.captureStateLayout()
            .varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    private final String prefix;

    /**
     * Makes the keychain; nothing is read or written yet. Only for Windows.
     *
     * @param application the start of each credential's name, such as "car-charge-planner", so the app's entries
     *     are told apart from others'
     */
    public CredentialManagerKeychain(String application) {
        this.prefix = application + ":";
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(Linker.Option.captureStateLayout());
            var found = arena.allocate(ADDRESS);
            var target = target(arena, name);
            int ok = call(() -> (int) Advapi32.CRED_READ.invokeExact(error, target, CRED_TYPE_GENERIC, 0, found));
            if (ok == 0) {
                return notFoundOrFail(error, "read");
            }
            var credential = found.get(ADDRESS, 0).reinterpret(CREDENTIAL.byteSize());
            try {
                int size = (int) BLOB_SIZE.get(credential, 0L);
                var blob = ((MemorySegment) BLOB.get(credential, 0L)).reinterpret(size);
                return Optional.of(new String(blob.toArray(JAVA_BYTE), StandardCharsets.UTF_8));
            } finally {
                call(() -> {
                    Advapi32.CRED_FREE.invokeExact(credential);
                    return null;
                });
            }
        }
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        try (var arena = Arena.ofConfined()) {
            var blob = arena.allocateFrom(JAVA_BYTE, value.getBytes(StandardCharsets.UTF_8));
            var credential = arena.allocate(CREDENTIAL);
            TYPE.set(credential, 0L, CRED_TYPE_GENERIC);
            TARGET_NAME.set(credential, 0L, target(arena, name));
            BLOB_SIZE.set(credential, 0L, (int) blob.byteSize());
            BLOB.set(credential, 0L, blob);
            PERSIST.set(credential, 0L, CRED_PERSIST_LOCAL_MACHINE);
            USER_NAME.set(credential, 0L, arena.allocateFrom(name, StandardCharsets.UTF_16LE));
            var error = arena.allocate(Linker.Option.captureStateLayout());
            int ok = call(() -> (int) Advapi32.CRED_WRITE.invokeExact(error, credential, 0));
            if (ok == 0) {
                throw failed("write", error);
            }
        }
    }

    @Override
    public void delete(String name) throws KeychainException {
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(Linker.Option.captureStateLayout());
            var target = target(arena, name);
            int ok = call(() -> (int) Advapi32.CRED_DELETE.invokeExact(error, target, CRED_TYPE_GENERIC, 0));
            if (ok == 0) {
                notFoundOrFail(error, "delete");
            }
        }
    }

    private static <T> T call(Advapi32.NativeCall<T> call) throws KeychainException {
        return Advapi32.call(call, e -> new KeychainException(KeychainException.Kind.UNAVAILABLE,
                "the Credential Manager could not be used (" + e.getClass().getSimpleName() + ")", e));
    }

    private MemorySegment target(Arena arena, String name) {
        return arena.allocateFrom(prefix + name, StandardCharsets.UTF_16LE);
    }

    private static Optional<String> notFoundOrFail(MemorySegment error, String action) throws KeychainException {
        if (lastError(error) == ERROR_NOT_FOUND) {
            return Optional.empty();
        }
        throw failed(action, error);
    }

    private static KeychainException failed(String action, MemorySegment error) {
        int code = lastError(error);
        var kind = code == ERROR_NO_SUCH_LOGON_SESSION ? KeychainException.Kind.UNAVAILABLE
                : KeychainException.Kind.REFUSED;
        return new KeychainException(kind, "the Credential Manager could not " + action + " (error " + code + ")");
    }

    private static int lastError(MemorySegment error) {
        return (int) GET_LAST_ERROR.get(error, 0L);
    }

    private static VarHandle field(String name) {
        return CREDENTIAL.varHandle(MemoryLayout.PathElement.groupElement(name));
    }
}
