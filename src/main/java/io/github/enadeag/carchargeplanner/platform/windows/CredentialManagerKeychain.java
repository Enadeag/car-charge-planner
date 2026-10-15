package io.github.enadeag.carchargeplanner.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The keychain on Windows: the Credential Manager, as generic credentials of this user on this computer. It calls
 * {@code advapi32.dll} through Java's own foreign function API, so it needs no library such as JNA, which would add
 * jars and unpack a native library into the temp folder.
 *
 * <p>Over ssh, without an interactive logon session, Windows refuses with error 1312; the engine then keeps the keys in
 * its private file.
 */
public final class CredentialManagerKeychain implements Keychain {

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

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
    private final MethodHandle credWrite;
    private final MethodHandle credRead;
    private final MethodHandle credDelete;
    private final MethodHandle credFree;

    /** @param application the start of each credential's name, such as "car-charge-planner" */
    public CredentialManagerKeychain(String application) {
        this.prefix = application + ":";
        var linker = Linker.nativeLinker();
        var advapi = SymbolLookup.libraryLookup("Advapi32", Arena.global());
        var lastError = Linker.Option.captureCallState("GetLastError");
        credWrite = linker.downcallHandle(advapi.findOrThrow("CredWriteW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), lastError);
        credRead = linker.downcallHandle(advapi.findOrThrow("CredReadW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), lastError);
        credDelete = linker.downcallHandle(advapi.findOrThrow("CredDeleteW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), lastError);
        credFree = linker.downcallHandle(advapi.findOrThrow("CredFree"), FunctionDescriptor.ofVoid(ADDRESS));
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        return withArena(arena -> {
            var error = arena.allocate(Linker.Option.captureStateLayout());
            var found = arena.allocate(ADDRESS);
            int ok = (int) credRead.invokeExact(error, target(arena, name), CRED_TYPE_GENERIC, 0, found);
            if (ok == 0) {
                return notFoundOrFail(error, "read");
            }
            var credential = found.get(ADDRESS, 0).reinterpret(CREDENTIAL.byteSize());
            try {
                int size = (int) BLOB_SIZE.get(credential, 0L);
                var blob = ((MemorySegment) BLOB.get(credential, 0L)).reinterpret(size);
                return Optional.of(new String(blob.toArray(JAVA_BYTE), StandardCharsets.UTF_8));
            } finally {
                credFree.invokeExact(credential);
            }
        });
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        withArena(arena -> {
            var blob = arena.allocateFrom(JAVA_BYTE, value.getBytes(StandardCharsets.UTF_8));
            var credential = arena.allocate(CREDENTIAL);
            TYPE.set(credential, 0L, CRED_TYPE_GENERIC);
            TARGET_NAME.set(credential, 0L, target(arena, name));
            BLOB_SIZE.set(credential, 0L, (int) blob.byteSize());
            BLOB.set(credential, 0L, blob);
            PERSIST.set(credential, 0L, CRED_PERSIST_LOCAL_MACHINE);
            USER_NAME.set(credential, 0L, arena.allocateFrom(name, StandardCharsets.UTF_16LE));
            var error = arena.allocate(Linker.Option.captureStateLayout());
            int ok = (int) credWrite.invokeExact(error, credential, 0);
            if (ok == 0) {
                throw failed("write", error);
            }
            return null;
        });
    }

    @Override
    public void delete(String name) throws KeychainException {
        withArena(arena -> {
            var error = arena.allocate(Linker.Option.captureStateLayout());
            int ok = (int) credDelete.invokeExact(error, target(arena, name), CRED_TYPE_GENERIC, 0);
            return ok == 0 ? notFoundOrFail(error, "delete") : Optional.empty();
        });
    }

    /** One call with the memory it needs; invokeExact declares Throwable, which is turned into a KeychainException. */
    private interface NativeCall<T> {
        T run(Arena arena) throws Throwable;
    }

    private static <T> T withArena(NativeCall<T> call) throws KeychainException {
        try (var arena = Arena.ofConfined()) {
            return call.run(arena);
        } catch (KeychainException e) {
            throw e;
        } catch (Throwable e) {
            throw new KeychainException("the Credential Manager could not be used (" + e.getClass().getSimpleName() + ")", e);
        }
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
        return new KeychainException("the Credential Manager could not " + action + " (error " + lastError(error) + ")");
    }

    private static int lastError(MemorySegment error) {
        return (int) GET_LAST_ERROR.get(error, 0L);
    }

    private static VarHandle field(String name) {
        return CREDENTIAL.varHandle(MemoryLayout.PathElement.groupElement(name));
    }
}
