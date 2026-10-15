package io.github.enadeag.carchargeplanner.desktop;

import java.util.Arrays;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;

/** Whether the app's windows are light or dark: as the system is, or always one of them. */
public enum Appearance {
    /** Light or dark as the system is, also when the system changes while the app runs. */
    SYSTEM("system"),
    /** Always light. */
    LIGHT("light"),
    /** Always dark. */
    DARK("dark");

    private final String code;

    Appearance(String code) {
        this.code = code;
    }

    /**
     * The name the app settings file writes.
     *
     * @return such as "dark"
     */
    public String code() {
        return code;
    }

    /**
     * The appearance with this name in the app settings file.
     *
     * @param code such as "light", in any case; may be empty or unknown
     * @return the appearance, or empty when there is none with the name
     */
    public static Optional<Appearance> ofCode(String code) {
        return Arrays.stream(values()).filter(appearance -> appearance.code.equalsIgnoreCase(code)).findFirst();
    }

    /**
     * The colour scheme the windows get with this appearance.
     *
     * @param system the system's colour scheme now; never null
     * @return the system's scheme for {@link #SYSTEM}, otherwise light or dark
     */
    public ColorScheme over(ColorScheme system) {
        return switch (this) {
            case SYSTEM -> system;
            case LIGHT -> ColorScheme.LIGHT;
            case DARK -> ColorScheme.DARK;
        };
    }

    /**
     * The colour scheme for the app's windows, following both the owner's choice and the system while the app runs.
     *
     * @param chosen the appearance the owner chose; changed on the JavaFX thread
     * @param system the system's colour scheme ({@code Platform.getPreferences().colorSchemeProperty()})
     * @return the scheme to give {@link SystemTheme}; it changes whenever either of them does
     */
    public static ObservableValue<ColorScheme> scheme(ObservableValue<Appearance> chosen,
            ObservableValue<ColorScheme> system) {
        return Bindings.createObjectBinding(() -> chosen.getValue().over(system.getValue()), chosen, system);
    }
}
