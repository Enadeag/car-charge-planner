package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.config.ForecastSelection;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The forecast providers as the forecast page shows them chosen.
 *
 * @param primary the provider asked first
 * @param fallback the provider asked when the primary has no usable forecast; empty for none
 */
public record ForecastEntry(ProviderId primary, Optional<ProviderId> fallback) {

    /**
     * The chosen providers, whose cards the page shows.
     *
     * @return the primary, then the fallback; one chosen for both only once
     */
    public List<ProviderId> chosen() {
        return Stream.concat(Stream.of(primary), fallback.stream()).distinct().toList();
    }

    /**
     * The selection to keep: the stored settings of every provider, with the settings the page shows laid over them.
     *
     * @param stored the stored settings that are not secret, per provider, also of providers this version does not
     *     know
     * @param shown the settings that are not secret as the page shows them, per provider it knows
     * @return the selection, without providers that have no setting
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when the fallback is the primary
     */
    public ForecastSelection selection(Map<ProviderId, Map<String, String>> stored,
            Map<ProviderId, Map<String, String>> shown) {
        var settings = new LinkedHashMap<>(stored);
        settings.putAll(shown);
        settings.values().removeIf(Map::isEmpty);
        return new ForecastSelection(primary, fallback, settings);
    }
}
