package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.util.Optional;

/**
 * What a forecast provider says about itself, in the owner's language for the providers this version knows; a provider
 * the app does not have texts for keeps the engine's English.
 */
public final class ProviderTexts {

    private final Messages messages;

    /**
     * Makes the texts.
     *
     * @param messages the texts, in the owner's language
     */
    public ProviderTexts(Messages messages) {
        this.messages = messages;
    }

    /**
     * The provider's description.
     *
     * @param info what the provider tells about itself
     * @return a few sentences about the service
     */
    public String description(ProviderInfo info) {
        return text(switch (info.id().value()) {
            case "open-meteo" -> TextKey.PROVIDER_OPEN_METEO;
            case "forecast-solar" -> TextKey.PROVIDER_FORECAST_SOLAR;
            default -> null;
        }).orElse(info.description());
    }

    /**
     * What is sent to the provider.
     *
     * @param info what the provider tells about itself
     * @return in plain words, what the service is sent
     */
    public String sends(ProviderInfo info) {
        return text(switch (info.id().value()) {
            case "open-meteo" -> TextKey.PROVIDER_OPEN_METEO_SENDS;
            case "forecast-solar" -> TextKey.PROVIDER_FORECAST_SOLAR_SENDS;
            default -> null;
        }).orElse(info.sends());
    }

    /**
     * A setting's name.
     *
     * @param setting the setting the provider asks for
     * @return such as "API key"
     */
    public String label(SettingSpec setting) {
        return text(switch (setting.key()) {
            case "api_key" -> TextKey.PROVIDER_API_KEY;
            case "base_url" -> TextKey.PROVIDER_BASE_URL;
            default -> null;
        }).orElse(setting.label());
    }

    /**
     * The sentence that explains a setting.
     *
     * @param info the provider that asks for it
     * @param setting the setting
     * @return what to fill in
     */
    public String hint(ProviderInfo info, SettingSpec setting) {
        return text(switch (info.id().value() + "/" + setting.key()) {
            case "open-meteo/api_key" -> TextKey.PROVIDER_OPEN_METEO_API_KEY_HINT;
            case "open-meteo/base_url" -> TextKey.PROVIDER_OPEN_METEO_BASE_URL_HINT;
            case "forecast-solar/api_key" -> TextKey.PROVIDER_FORECAST_SOLAR_API_KEY_HINT;
            default -> null;
        }).orElse(setting.hint());
    }

    private Optional<String> text(TextKey key) {
        return Optional.ofNullable(key).map(messages::text);
    }
}
