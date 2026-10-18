package io.github.enadeag.carchargeplanner.platform.windows;

import io.github.enadeag.carchargeplanner.desktop.system.PanelShade;
import java.io.IOException;

/**
 * Whether the Windows taskbar is light or dark. Windows sets it apart from the apps' mode: Windows 10 has a dark taskbar
 * with light apps unless the owner changes it, so the colour scheme JavaFX reports does not tell. It reads the
 * setting from the registry through {@link Advapi32}.
 */
public final class TaskbarShade {

    private static final String KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    private static final String VALUE = "SystemUsesLightTheme";

    /** Makes the reader; the registry is read on each {@link #read()}, for the setting can change while the app runs. */
    public TaskbarShade() {
    }

    /**
     * Reads the taskbar's shade as Windows has it now.
     *
     * @return {@link PanelShade#LIGHT} when Windows says the system uses the light theme; dark otherwise, also when
     *     the setting is missing (Windows before 10 version 1903 had only a dark taskbar) or cannot be read
     */
    public PanelShade read() {
        try {
            var light = Advapi32.readNumber(KEY, VALUE);
            return light.isPresent() && light.getAsInt() == 1 ? PanelShade.LIGHT : PanelShade.DARK;
        } catch (IOException e) {
            // a taskbar we cannot read is most likely the dark one Windows starts with
            return PanelShade.DARK;
        }
    }
}
