package nl.enadeag.carchargeplanner.android.settings

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import nl.enadeag.carchargeplanner.android.form.ConfigForm
import nl.enadeag.carchargeplanner.android.form.ForecastForm
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges

/**
 * One Settings screen's Save: its forms are checked against the config it shows, every refused value showing its
 * problem, and when none is refused only the screen's part is laid over the settings file as it is then, with the
 * API keys first (#315).
 *
 * @param forms the screen's forms; a [ForecastForm] among them brings the API keys
 * @param saves the app's saves, which outlive the screen
 * @param screen where the forms are read, the screen's own thread, as they are not made for others
 */
class SettingsEdit(private val forms: List<ConfigForm>, private val saves: Saves,
    private val screen: CoroutineContext) {

    /**
     * Checks the forms and saves them when every value is accepted.
     *
     * @param shown the config the screen shows, as read when it opened
     * @return the save; null when a value is refused, and nothing is saved
     */
    fun save(shown: EngineConfig): Job? {
        val refused = forms.map { it.check(shown) == null }
        if (refused.any { it }) {
            return null
        }
        val keys = forms.filterIsInstance<ForecastForm>().firstOrNull()?.keyChanges() ?: KeyChanges.NONE
        return saves.update(keys) { current ->
            withContext(screen) {
                forms.fold(current) { config, form ->
                    form.read(config) ?: throw IllegalArgumentException(
                        "the settings file changed meanwhile, and a value no longer fits it")
                }
            }
        }
    }

    /**
     * Whether the owner changed something on the screen, so Back asks whether to throw it away.
     *
     * @return true when a form was edited
     */
    fun isEdited(): Boolean = forms.any { it.isEdited() }
}
