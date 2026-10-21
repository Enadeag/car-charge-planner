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

/**
 * The functions of {@code kernel32.dll} the app calls, through Java's own foreign function API: an event to wait for,
 * which the registry signals at a change. Each function is looked up once, when this class is first used, which is
 * only on Windows.
 */
final class Kernel32 {

    private static final int INFINITE = 0xFFFF_FFFF;
    private static final int WAIT_OBJECT_0 = 0;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY = SymbolLookup.libraryLookup("Kernel32", Arena.global());

    private static final MethodHandle CREATE_EVENT = function("CreateEventW",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle WAIT_FOR_SINGLE_OBJECT = function("WaitForSingleObject",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle CLOSE_HANDLE = function("CloseHandle", FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private Kernel32() {
    }

    /**
     * Makes an event without a name that resets itself once a wait has seen it; {@link #close} closes it again.
     *
     * @return the event's handle, not signalled yet
     * @throws IOException when Windows refuses
     */
    static MemorySegment newEvent() throws IOException {
        var event = Advapi32.call(() -> (MemorySegment) CREATE_EVENT.invokeExact(MemorySegment.NULL, 0, 0,
                MemorySegment.NULL), e -> new IOException("could not make an event", e));
        if (event.equals(MemorySegment.NULL)) {
            throw new IOException("Windows could not make an event");
        }
        return event;
    }

    /**
     * Waits, as long as it takes, until an event is signalled.
     *
     * @param event the event's handle, from {@link #newEvent()}
     * @throws IOException when the wait fails, such as for a handle that was closed
     */
    static void await(MemorySegment event) throws IOException {
        int status = Advapi32.call(() -> (int) WAIT_FOR_SINGLE_OBJECT.invokeExact(event, INFINITE),
                e -> new IOException("could not wait for an event", e));
        if (status != WAIT_OBJECT_0) {
            throw new IOException("Windows could not wait for an event: " + status);
        }
    }

    /**
     * Closes a handle, such as an event's.
     *
     * @param handle the handle to close
     * @throws IOException when Windows refuses
     */
    static void close(MemorySegment handle) throws IOException {
        int closed = Advapi32.call(() -> (int) CLOSE_HANDLE.invokeExact(handle),
                e -> new IOException("could not close a handle", e));
        if (closed == 0) {
            throw new IOException("Windows could not close a handle");
        }
    }

    private static MethodHandle function(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(LIBRARY.findOrThrow(name), descriptor);
    }
}
