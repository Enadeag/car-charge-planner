package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
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
        return text(switch (setting.name()) {
            case "api_key" -> TextKey.PROVIDER_API_KEY;
            case "base_url" -> TextKey.PROVIDER_BASE_URL;
            default -> null;
        }).orElse(setting.label());
    }

    /**
     * The sentence that explains a setting.
     *
     * @param info the provider that asks for it
     * @param setting the setting the provider declares; its own hint is shown when the app has no translation for it
     * @return what to fill in
     */
    public String hint(ProviderInfo info, SettingSpec setting) {
        return text(switch (info.id().value() + "/" + setting.name()) {
            case "open-meteo/api_key" -> TextKey.PROVIDER_OPEN_METEO_API_KEY_HINT;
            case "open-meteo/base_url" -> TextKey.PROVIDER_OPEN_METEO_BASE_URL_HINT;
            case "forecast-solar/api_key" -> TextKey.PROVIDER_FORECAST_SOLAR_API_KEY_HINT;
            default -> null;
        }).orElse(setting.hint());
    }

    /**
     * The name of the fold with a provider's optional settings, by what is in it.
     *
     * @param info what the provider tells about itself; one without optional settings has no fold
     * @return "API key and server" for an API key and a server address, "API key" for an API key alone, "More
     *     settings" for any other optional settings
     */
    public String foldName(ProviderInfo info) {
        var optional = info.settings().stream().filter(SettingSpec::optional).toList();
        var keys = optional.stream().filter(SettingSpec::secret).count();
        var servers = optional.stream()
                .filter(setting -> !setting.secret() && setting.format() == SettingFormat.SERVER_ADDRESS).count();
        if (keys == 1 && servers == 1 && optional.size() == 2) {
            return messages.text(TextKey.PROVIDER_FOLD_KEY_AND_SERVER);
        }
        if (keys == 1 && optional.size() == 1) {
            return messages.text(TextKey.PROVIDER_API_KEY);
        }
        return messages.text(TextKey.MORE_SETTINGS);
    }

    private Optional<String> text(TextKey key) {
        return Optional.ofNullable(key).map(messages::text);
    }
}
