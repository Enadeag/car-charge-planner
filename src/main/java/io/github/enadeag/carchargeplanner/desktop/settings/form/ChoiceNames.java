package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.desktop.app.Appearance;
import io.github.enadeag.carchargeplanner.desktop.app.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.app.Units;
import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Optional;

/** What a choice field shows for each of its choices, in the owner's language. */
public final class ChoiceNames {

    private final Messages messages;

    /**
     * Makes the names.
     *
     * @param messages the texts, in the owner's language
     */
    public ChoiceNames(Messages messages) {
        this.messages = messages;
    }

    /**
     * How the app shows itself.
     *
     * @param mode the way the app shows itself, to name
     * @return such as "Tray icon"
     */
    public String mode(DisplayMode mode) {
        return messages.text(switch (mode) {
            case AUTOMATIC -> TextKey.MODE_AUTOMATIC;
            case TRAY -> TextKey.MODE_TRAY;
            case WINDOW -> TextKey.MODE_WINDOW;
        });
    }

    /**
     * A language by its own name, such as "Nederlands", so that the owner finds it whatever the app speaks now.
     *
     * @param language the choice; empty for the system's language
     * @return the language's own name, or the words for the system's language
     */
    public String language(Optional<Language> language) {
        return language.map(Language::autonym).orElseGet(() -> messages.text(TextKey.LANGUAGE_SYSTEM));
    }

    /**
     * How the app looks.
     *
     * @param appearance the look to name: the system's, light or dark
     * @return such as "Dark"
     */
    public String appearance(Appearance appearance) {
        return messages.text(switch (appearance) {
            case SYSTEM -> TextKey.APPEARANCE_SYSTEM;
            case LIGHT -> TextKey.APPEARANCE_LIGHT;
            case DARK -> TextKey.APPEARANCE_DARK;
        });
    }

    /**
     * The units distances and consumption show in.
     *
     * @param units the system of units to name
     * @return such as "Miles"
     */
    public String units(Units units) {
        return messages.text(switch (units) {
            case METRIC -> TextKey.UNITS_METRIC;
            case IMPERIAL -> TextKey.UNITS_IMPERIAL;
        });
    }

    /**
     * The phases the charger charges on.
     *
     * @param phases the number of phases the charger uses, to name
     * @return such as "Three phases"
     */
    public String phases(Phases phases) {
        return messages.text(phases == Phases.ONE ? TextKey.PHASES_ONE : TextKey.PHASES_THREE);
    }

    /**
     * A weekday, written in full and with a capital as it starts the row.
     *
     * @param day the day of the week to name, in the owner's language
     * @return such as "Monday" or "Maandag"
     */
    public String weekday(DayOfWeek day) {
        var locale = messages.language().locale();
        var name = day.getDisplayName(TextStyle.FULL, locale);
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /**
     * A forecast provider by its name; one the config names but this version does not know, by its id.
     *
     * @param id the provider to name; one that is not among {@code known} is named by its id
     * @param known what the providers this version knows tell about themselves
     * @return such as "Open-Meteo"
     */
    public String provider(ProviderId id, List<ProviderInfo> known) {
        return known.stream().filter(info -> info.id().equals(id)).findFirst().map(ProviderInfo::name)
                .orElseGet(() -> messages.text(TextKey.PROVIDER_UNKNOWN, id.value()));
    }

    /**
     * The fallback provider, or that there is none.
     *
     * @param id the choice; empty for no fallback
     * @param known what the providers this version knows tell about themselves
     * @return the provider's name, or the words for none
     */
    public String fallback(Optional<ProviderId> id, List<ProviderInfo> known) {
        return id.map(chosen -> provider(chosen, known)).orElseGet(() -> messages.text(TextKey.FALLBACK_NONE));
    }
}
