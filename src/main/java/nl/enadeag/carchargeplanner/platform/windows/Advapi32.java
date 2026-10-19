package nl.enadeag.carchargeplanner.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * The functions of {@code advapi32.dll} the app calls, through Java's own foreign function API: the registry below
 * HKEY_CURRENT_USER and the Credential Manager. Each function is looked up once, when this class is first used, which
 * is only on Windows. No library such as JNA: it would add jars and unpack a native library into the temp folder.
 */
final class Advapi32 {

    /** HKEY_CURRENT_USER: the handle (HKEY) 0x80000001, sign-extended to 64 bits as winreg.h does. */
    private static final MemorySegment CURRENT_USER = MemorySegment.ofAddress(0xFFFF_FFFF_8000_0001L);
    private static final int REG_SZ = 1;
    private static final int RRF_RT_REG_SZ = 0x2;
    private static final int RRF_RT_REG_DWORD = 0x10;
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_FILE_NOT_FOUND = 2;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY = SymbolLookup.libraryLookup("Advapi32", Arena.global());
    private static final Linker.Option LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    private static final MethodHandle REG_GET_VALUE = function("RegGetValueW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle REG_SET_KEY_VALUE = function("RegSetKeyValueW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle REG_DELETE_KEY_VALUE = function("RegDeleteKeyValueW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /**
     * {@code CredWriteW(error state, CREDENTIALW*, flags)}: writes a credential; returns 0 on failure, with the error
     * in the captured state ({@link Linker.Option#captureStateLayout()}).
     */
    static final MethodHandle CRED_WRITE = function("CredWriteW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), LAST_ERROR);
    /**
     * {@code CredReadW(error state, target name, type, flags, CREDENTIALW**)}: reads a credential, which
     * {@link #CRED_FREE} frees again; returns 0 on failure, with the error in the captured state.
     */
    static final MethodHandle CRED_READ = function("CredReadW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), LAST_ERROR);
    /**
     * {@code CredDeleteW(error state, target name, type, flags)}: removes a credential; returns 0 on failure, with the
     * error in the captured state.
     */
    static final MethodHandle CRED_DELETE = function("CredDeleteW",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT), LAST_ERROR);
    /** {@code CredFree(buffer)}: frees what {@link #CRED_READ} gave. */
    static final MethodHandle CRED_FREE = function("CredFree", FunctionDescriptor.ofVoid(ADDRESS));

    private Advapi32() {
    }

    /**
     * One call through a method handle of this class.
     *
     * @param <T> what the call gives
     */
    @FunctionalInterface
    interface NativeCall<T> {

        /**
         * Makes the call.
         *
         * @return what the function gave, such as its status
         * @throws Throwable because {@code MethodHandle.invokeExact} declares it
         */
        T run() throws Throwable;
    }

    /**
     * Makes a call, turning the {@code Throwable} that {@code invokeExact} declares into the caller's exception. A
     * {@code RuntimeException} or {@code Error} is passed on as it is: it is a mistake in the call, not Windows
     * refusing.
     *
     * @param <T> what the call gives
     * @param <E> the caller's exception
     * @param call the call, such as {@code () -> (int) CRED_DELETE.invokeExact(...)}
     * @param failure makes the caller's exception from the checked one the call threw
     * @return what the call gave
     * @throws E when the call threw a checked exception
     */
    static <T, E extends Exception> T call(NativeCall<T> call, Function<Throwable, E> failure) throws E {
        try {
            return call.run();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw failure.apply(e);
        }
    }

    /**
     * A text as Windows' wide strings are, with the terminating null character the registry counts in a value's size.
     *
     * @param arena holds the memory as long as the call needs it
     * @param text the text to hand to Windows, such as a key's path, a value's name or a text value
     * @return the text in UTF-16LE with a null character at the end
     */
    static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    /**
     * Reads a text value (REG_SZ) below HKEY_CURRENT_USER.
     *
     * @param key the key's path below HKEY_CURRENT_USER
     * @param name the value's name
     * @return the text; empty when the key or the value does not exist
     * @throws IOException when Windows refuses, or the value is not a text
     */
    static Optional<String> readText(String key, String name) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = wide(arena, key);
            var value = wide(arena, name);
            var size = arena.allocate(JAVA_INT);
            int status = call(() -> (int) REG_GET_VALUE.invokeExact(CURRENT_USER, path, value, RRF_RT_REG_SZ,
                    MemorySegment.NULL, MemorySegment.NULL, size), e -> failed("read", key, e));
            if (status == ERROR_FILE_NOT_FOUND) {
                return Optional.empty();
            }
            check(status, "read", key);
            var data = arena.allocate(size.get(JAVA_INT, 0));
            check(call(() -> (int) REG_GET_VALUE.invokeExact(CURRENT_USER, path, value, RRF_RT_REG_SZ,
                    MemorySegment.NULL, data, size), e -> failed("read", key, e)), "read", key);
            return Optional.of(data.getString(0, StandardCharsets.UTF_16LE));
        }
    }

    /**
     * Reads a number value (REG_DWORD) below HKEY_CURRENT_USER.
     *
     * @param key the key's path below HKEY_CURRENT_USER
     * @param name the value's name
     * @return the number; empty when the key or the value does not exist
     * @throws IOException when Windows refuses, or the value is not a number
     */
    static OptionalInt readNumber(String key, String name) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = wide(arena, key);
            var value = wide(arena, name);
            var data = arena.allocate(JAVA_INT);
            var size = arena.allocateFrom(JAVA_INT, Integer.BYTES);
            int status = call(() -> (int) REG_GET_VALUE.invokeExact(CURRENT_USER, path, value, RRF_RT_REG_DWORD,
                    MemorySegment.NULL, data, size), e -> failed("read", key, e));
            if (status == ERROR_FILE_NOT_FOUND) {
                return OptionalInt.empty();
            }
            check(status, "read", key);
            return OptionalInt.of(data.get(JAVA_INT, 0));
        }
    }

    /**
     * Writes a text value (REG_SZ) below HKEY_CURRENT_USER, making the key when it does not exist.
     *
     * @param key the key's path below HKEY_CURRENT_USER
     * @param name the value's name
     * @param text the text to write
     * @throws IOException when Windows refuses
     */
    static void writeText(String key, String name, String text) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = wide(arena, key);
            var value = wide(arena, name);
            var data = wide(arena, text);
            check(call(() -> (int) REG_SET_KEY_VALUE.invokeExact(CURRENT_USER, path, value, REG_SZ, data,
                    Math.toIntExact(data.byteSize())), e -> failed("write", key, e)), "write", key);
        }
    }

    /**
     * Removes a value below HKEY_CURRENT_USER; nothing happens when it does not exist.
     *
     * @param key the key's path below HKEY_CURRENT_USER
     * @param name the value's name
     * @throws IOException when Windows refuses
     */
    static void deleteValue(String key, String name) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = wide(arena, key);
            var value = wide(arena, name);
            int status = call(() -> (int) REG_DELETE_KEY_VALUE.invokeExact(CURRENT_USER, path, value),
                    e -> failed("remove", key, e));
            if (status != ERROR_FILE_NOT_FOUND) {
                check(status, "remove", key);
            }
        }
    }

    private static MethodHandle function(String name, FunctionDescriptor descriptor, Linker.Option... options) {
        return LINKER.downcallHandle(LIBRARY.findOrThrow(name), descriptor, options);
    }

    private static IOException failed(String what, String key, Throwable cause) {
        return new IOException("could not " + what + " the key " + key, cause);
    }

    private static void check(int status, String what, String key) throws IOException {
        if (status != ERROR_SUCCESS) {
            throw new IOException("Windows could not " + what + " the key " + key + ": error " + status);
        }
    }
}
