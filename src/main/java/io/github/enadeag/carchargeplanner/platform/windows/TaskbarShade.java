package io.github.enadeag.carchargeplanner.platform.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.enadeag.carchargeplanner.desktop.PanelShade;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;

/**
 * Whether the Windows taskbar is light or dark. Windows sets it apart from the apps' mode: Windows 10 has a dark taskbar
 * with light apps unless the owner changes it, so the colour scheme JavaFX reports does not tell. It reads the
 * setting from the registry through Java's own foreign function API, as {@link CredentialManagerKeychain} does.
 */
public final class TaskbarShade {

    /** HKEY_CURRENT_USER: the handle (HKEY) 0x80000001, sign-extended to 64 bits as winreg.h does. */
    private static final long CURRENT_USER = 0xFFFF_FFFF_8000_0001L;
    private static final int RRF_RT_REG_DWORD = 0x10;
    private static final String KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String VALUE = "SystemUsesLightTheme";

    /** Makes the reader; the registry is read on each {@link #read()}. Only for Windows. */
    public TaskbarShade() {
        // the setting can change while the app runs
    }

    /**
     * Reads the taskbar's shade as Windows has it now.
     *
     * @return {@link PanelShade#LIGHT} when Windows says the system uses the light theme; dark otherwise, also when
     *     the setting is missing (Windows before 10 version 1903 had only a dark taskbar) or cannot be read
     */
    public PanelShade read() {
        try (var arena = Arena.ofConfined()) {
            var regGetValue = Linker.nativeLinker().downcallHandle(
                    SymbolLookup.libraryLookup("Advapi32", arena).findOrThrow("RegGetValueW"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            var data = arena.allocate(JAVA_INT);
            var size = arena.allocateFrom(JAVA_INT, Integer.BYTES);
            int status = (int) regGetValue.invokeExact(MemorySegment.ofAddress(CURRENT_USER),
                    arena.allocateFrom(KEY, StandardCharsets.UTF_16LE), arena.allocateFrom(VALUE, StandardCharsets.UTF_16LE),
                    RRF_RT_REG_DWORD, MemorySegment.NULL, data, size);
            return status == 0 && data.get(JAVA_INT, 0) == 1 ? PanelShade.LIGHT : PanelShade.DARK;
        } catch (Throwable e) {
            // invokeExact declares Throwable; a taskbar we cannot read is most likely the dark one Windows starts with
            return PanelShade.DARK;
        }
    }
}
