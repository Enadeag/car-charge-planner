package io.github.enadeag.carchargeplanner.platform.windows;

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

/**
 * The user's real Run key, or for tests a key of the same kind, reached through {@code advapi32.dll} with Java's own
 * foreign function API, as {@link TaskbarShade} does. Not {@code reg.exe}: Java quotes the arguments of a started
 * program in its own way, and the command line in the value has quotes of its own, which would have to survive both.
 */
public final class RegistryRunKey implements RunKey {

    /** HKEY_CURRENT_USER: the handle (HKEY) 0x80000001, sign-extended to 64 bits as winreg.h does. */
    private static final long CURRENT_USER = 0xFFFF_FFFF_8000_0001L;
    /** The Run key's path below HKEY_CURRENT_USER. */
    public static final String RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final int REG_SZ = 1;
    private static final int RRF_RT_REG_SZ = 0x2;
    private static final int ERROR_SUCCESS = 0;
    private static final int ERROR_FILE_NOT_FOUND = 2;

    private final String key;
    private final MethodHandle regGetValue;
    private final MethodHandle regSetKeyValue;
    private final MethodHandle regDeleteKeyValue;

    /**
     * Finds the registry's functions; nothing is read or written yet. Only for Windows.
     *
     * @param key the key's path below HKEY_CURRENT_USER: {@link #RUN}, or another key for a test, which leaves the
     *     programs started at login alone; made when a value is first written
     */
    public RegistryRunKey(String key) {
        this.key = key;
        var linker = Linker.nativeLinker();
        var advapi = SymbolLookup.libraryLookup("Advapi32", Arena.global());
        regGetValue = linker.downcallHandle(advapi.findOrThrow("RegGetValueW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        regSetKeyValue = linker.downcallHandle(advapi.findOrThrow("RegSetKeyValueW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
        regDeleteKeyValue = linker.downcallHandle(advapi.findOrThrow("RegDeleteKeyValueW"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    }

    @Override
    public Optional<String> get(String name) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = text(arena, key);
            var value = text(arena, name);
            var size = arena.allocate(JAVA_INT);
            int status = call("read", () -> (int) regGetValue.invokeExact(MemorySegment.ofAddress(CURRENT_USER), path,
                    value, RRF_RT_REG_SZ, MemorySegment.NULL, MemorySegment.NULL, size));
            if (status == ERROR_FILE_NOT_FOUND) {
                return Optional.empty();
            }
            check(status, "read");
            var data = arena.allocate(size.get(JAVA_INT, 0));
            check(call("read", () -> (int) regGetValue.invokeExact(MemorySegment.ofAddress(CURRENT_USER), path, value,
                    RRF_RT_REG_SZ, MemorySegment.NULL, data, size)), "read");
            return Optional.of(data.getString(0, StandardCharsets.UTF_16LE));
        }
    }

    @Override
    public void set(String name, String commandLine) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = text(arena, key);
            var value = text(arena, name);
            var data = text(arena, commandLine);
            check(call("write", () -> (int) regSetKeyValue.invokeExact(MemorySegment.ofAddress(CURRENT_USER), path, value,
                    REG_SZ, data, Math.toIntExact(data.byteSize()))), "write");
        }
    }

    @Override
    public void delete(String name) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var path = text(arena, key);
            var value = text(arena, name);
            int status = call("remove", () -> (int) regDeleteKeyValue.invokeExact(MemorySegment.ofAddress(CURRENT_USER),
                    path, value));
            if (status != ERROR_FILE_NOT_FOUND) {
                check(status, "remove");
            }
        }
    }

    /** A call into advapi32; {@code invokeExact} declares Throwable. */
    private interface NativeCall {
        int status() throws Throwable;
    }

    private static int call(String what, NativeCall call) throws IOException {
        try {
            return call.status();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("could not " + what + " the key", e);
        }
    }

    /** With its terminating null character, which the registry counts in a value's size. */
    private static MemorySegment text(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    private void check(int status, String what) throws IOException {
        if (status != ERROR_SUCCESS) {
            throw new IOException("Windows could not " + what + " the key " + key + ": error " + status);
        }
    }
}
