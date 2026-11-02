package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ForecastSelection
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * The forecast providers: the primary, an optional fallback that is not the primary, and each provider's settings.
 * The choices and the settings that are not secret are kept in the [SavedStateHandle]; the secret ones, the API keys,
 * only in memory, so they never reach the saved state: after the system ended the app they come back as stored. A
 * key the owner leaves untouched stays as stored, an emptied one is removed (`Engine.applyKeyChanges`).
 *
 * @param handle where the choices and the settings that are not secret are kept
 * @param providers what the known providers tell about themselves, in the order offered
 */
class ForecastForm(private val handle: SavedStateHandle, private val providers: List<ProviderInfo>) : ConfigForm {

    /** Where a problem of the form shows. */
    sealed interface Target {

        /** The choice of the fallback provider, which may not be the primary. */
        data object Fallback : Target

        /**
         * One setting of a provider.
         *
         * @property provider the provider
         * @property name the setting's name, such as `api_key`
         */
        data class Setting(val provider: ProviderId, val name: String) : Target
    }

    private companion object {
        const val PRIMARY = "forecast.primary"
        const val FALLBACK = "forecast.fallback"
        const val EDITED = "forecast.edited"
    }

    private val typedKeys = HashMap<Target.Setting, String>()
    private var stored: StoredKeys = StoredKeys.Read(Secrets.NONE)
    private val problemsNow = MutableStateFlow<Map<Target, FieldProblem>>(emptyMap())

    /** The problems shown now, by where they show; each until its field is edited. */
    val problems: StateFlow<Map<Target, FieldProblem>> = problemsNow.asStateFlow()

    /**
     * The primary provider as chosen.
     *
     * @return its id; null before the form was loaded
     */
    fun primary(): ProviderId? = handle.get<String>(PRIMARY)?.let(::ProviderId)

    /**
     * The fallback provider as chosen.
     *
     * @return its id; null for none
     */
    fun fallback(): ProviderId? = handle.get<String>(FALLBACK)?.takeIf { it.isNotEmpty() }?.let(::ProviderId)

    /**
     * Chooses the primary provider.
     *
     * @param provider the provider asked first
     */
    fun choosePrimary(provider: ProviderId) {
        handle[PRIMARY] = provider.value()
        markEdited("primary")
        clear(Target.Fallback)
    }

    /**
     * Chooses the fallback provider.
     *
     * @param provider the provider asked when the primary has no forecast; null for none
     */
    fun chooseFallback(provider: ProviderId?) {
        handle[FALLBACK] = provider?.value().orEmpty()
        markEdited("fallback")
        clear(Target.Fallback)
    }

    /**
     * Takes the stored API keys, as they were read for the screen; the keys typed so far are dropped.
     *
     * @param keys the keys, or that the keychain could not give them
     */
    fun loadKeys(keys: StoredKeys) {
        stored = keys
        typedKeys.clear()
    }

    /**
     * Whether the stored keys could be read, so the screen can say they could not and may be entered again.
     *
     * @return false when the keychain could not give them
     */
    fun keysReadable(): Boolean = stored is StoredKeys.Read

    /**
     * A setting's text: a secret one as typed, else as stored.
     *
     * @param provider the provider
     * @param name the setting's name
     * @return what the field holds
     */
    fun text(provider: ProviderId, name: String): String {
        val target = Target.Setting(provider, name)
        return if (isSecret(target)) typedKeys[target] ?: storedSecrets().of(provider)[name].orEmpty()
        else handle.get<String>(plainKey(target)).orEmpty()
    }

    /**
     * Puts what the owner typed in a setting: its problem goes. A secret one stays in memory only.
     *
     * @param provider the provider
     * @param name the setting's name
     * @param text what the field holds now; empty removes a stored key at Save
     */
    fun edit(provider: ProviderId, name: String, text: String) {
        val target = Target.Setting(provider, name)
        if (isSecret(target)) {
            typedKeys[target] = text
        } else {
            handle[plainKey(target)] = text
            markEdited(plainKey(target))
        }
        clear(target)
    }

    /**
     * What Save applies to the stored keys: what the owner typed over them, an emptied key removed.
     *
     * @return the changes; empty when no key was typed
     */
    fun keyChanges(): KeyChanges {
        val before = storedSecrets()
        val shown = HashMap<ProviderId, MutableMap<String, String>>()
        before.byProvider().forEach { (id, values) -> shown[id] = values.toMutableMap() }
        typedKeys.forEach { (target, value) -> shown.getOrPut(target.provider) { mutableMapOf() }[target.name] = value }
        return KeyChanges.between(before, Secrets(shown))
    }

    /**
     * The secret settings as entered, for a connection check of what the owner typed.
     *
     * @param provider the provider
     * @return its secret settings with a value, as typed or stored
     */
    fun secretsOf(provider: ProviderId): Map<String, String> = infoOf(provider)?.settings().orEmpty()
        .filter { it.secret() }.associate { it.name() to text(provider, it.name()) }.filterValues { it.isNotEmpty() }

    /**
     * The settings that are not secret as entered.
     *
     * @param provider the provider
     * @return its settings that are not secret with a value
     */
    fun plainOf(provider: ProviderId): Map<String, String> = infoOf(provider)?.settings().orEmpty()
        .filter { !it.secret() }.associate { it.name() to text(provider, it.name()) }.filterValues { it.isNotEmpty() }

    override fun load(config: EngineConfig) {
        val forecast = config.forecast()
        handle[PRIMARY] = forecast.primary().value()
        handle[FALLBACK] = forecast.fallback().getOrNull()?.value().orEmpty()
        for (info in providers) {
            for (setting in info.settings().filter { !it.secret() }) {
                handle[plainKey(Target.Setting(info.id(), setting.name()))] =
                    forecast.settingsOf(info.id())[setting.name()].orEmpty()
            }
        }
        handle[EDITED] = ArrayList<String>()
        typedKeys.clear()
    }

    override fun read(config: EngineConfig): EngineConfig? = evaluate(config).first

    override fun check(config: EngineConfig): EngineConfig? {
        val (result, problems) = evaluate(config)
        problemsNow.value = problems
        return result
    }

    override fun isEdited(): Boolean = handle.get<ArrayList<String>>(EDITED).orEmpty().isNotEmpty() ||
        !keyChanges().isEmpty

    private fun evaluate(config: EngineConfig): Pair<EngineConfig?, Map<Target, FieldProblem>> {
        val problems = linkedMapOf<Target, FieldProblem>()
        val primary = primary() ?: config.forecast().primary()
        val fallback = if (handle.contains(FALLBACK)) fallback() else config.forecast().fallback().getOrNull()
        for (id in listOfNotNull(primary, fallback).distinct()) {
            problems.putAll(settingProblems(id))
        }
        val settings = LinkedHashMap(config.forecast().settings())
        providers.forEach { info -> settings[info.id()] = plainOf(info.id()) }
        settings.values.removeIf { it.isEmpty() }
        val selection = try {
            ForecastSelection(primary, Optional.ofNullable(fallback), settings)
        } catch (refused: InvalidSetting) {
            problems[Target.Fallback] = FieldProblem.Refused(refused.rule())
            null
        }
        if (problems.isNotEmpty() || selection == null) {
            return null to problems
        }
        return config.withForecast(selection) to emptyMap()
    }

    /** A required setting without a value, and a value of the wrong form, the latter first. */
    private fun settingProblems(provider: ProviderId): Map<Target, FieldProblem> {
        val info = infoOf(provider) ?: return emptyMap()
        val problems = linkedMapOf<Target, FieldProblem>()
        val entered = info.settingsFrom(plainOf(provider), secretsOf(provider))
        val missing = info.missingRequired(entered)
        for (setting in info.settings()) {
            val target = Target.Setting(provider, setting.name())
            val value = entered[setting.name()].orEmpty()
            if (!setting.format().accepts(value)) {
                problems[target] = when (setting.format()) {
                    SettingFormat.SERVER_ADDRESS -> FieldProblem.ServerAddress
                    SettingFormat.LETTERS_AND_DIGITS -> FieldProblem.LettersAndDigits
                    SettingFormat.TEXT -> FieldProblem.OneLine
                }
            } else if (setting.label() in missing && !(setting.secret() && !keysReadable())) {
                // a key the keychain could not give may well be stored: it is not asked for again
                problems[target] = FieldProblem.Required
            }
        }
        return problems
    }

    private fun storedSecrets(): Secrets = (stored as? StoredKeys.Read)?.keys ?: Secrets.NONE

    private fun infoOf(provider: ProviderId) = providers.firstOrNull { it.id() == provider }

    private fun isSecret(target: Target.Setting) =
        infoOf(target.provider)?.settings().orEmpty().any { it.name() == target.name && it.secret() }

    private fun plainKey(target: Target.Setting) = "forecast.plain.${target.provider.value()}.${target.name}"

    private fun markEdited(what: String) {
        handle[EDITED] = ArrayList(handle.get<ArrayList<String>>(EDITED).orEmpty() + what)
    }

    private fun clear(target: Target) {
        if (target in problemsNow.value) {
            problemsNow.value = problemsNow.value - target
        }
    }
}
