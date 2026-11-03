package nl.enadeag.carchargeplanner.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.FieldProblem
import nl.enadeag.carchargeplanner.android.form.ForecastForm
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.setup.SetUpCheck
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceRow
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.problemText
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.android.ui.setup.Edits
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton

/**
 * The forecast providers: the primary, the fallback or none, each with Check connection on the values as typed, and a
 * section per provider with its settings. An API key is masked, with Show and Hide, filled with the stored key; when
 * the stored keys could not be read, a note says so and a key left empty stays as stored.
 *
 * @param session the forecast's form, the API keys as read, and the checks
 * @param edits counts each edit, so the fields show it
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun ForecastPage(session: SettingsSession, edits: Edits, problems: ProblemFocus) {
    val forecast = session.forms.forecast
    val shown by forecast.problems.collectAsStateWithLifecycle()
    val keys by session.keys.collectAsStateWithLifecycle()
    val providers = session.providers()
    val primary = edits.shown { forecast.primary() }
    val fallback = edits.shown { forecast.fallback() }

    ChoiceRow(stringResource(R.string.forecast_primary), stringResource(R.string.forecast_primary_hint),
        providers.map { it.id() }, primary, { session.providerName(it) },
        { id -> edits.make { forecast.choosePrimary(id) } })
    primary?.let { CheckConnection(session, SettingsSession.Role.PRIMARY, session.primaryCheck, it) }
    val fallbackProblem = shown[ForecastForm.Target.Fallback]
    ChoiceRow(stringResource(R.string.forecast_fallback), stringResource(R.string.forecast_fallback_hint),
        listOf<ProviderId?>(null) + providers.map { it.id() }, fallback,
        { id -> id?.let { session.providerName(it) } ?: stringResource(R.string.forecast_none) },
        { id -> edits.make { forecast.chooseFallback(id) } }, problem = fallbackProblem,
        focus = rememberFieldFocus(problems, 0, fallbackProblem != null))
    fallback?.let { CheckConnection(session, SettingsSession.Role.FALLBACK, session.fallbackCheck, it) }

    when {
        keys == null -> Waiting(stringResource(R.string.keys_reading))
        !forecast.keysReadable() -> Text(stringResource(R.string.keys_unreadable),
            color = MaterialTheme.colorScheme.error)
    }
    providers.forEachIndexed { index, info ->
        SectionHeading(info.name())
        info.settings().forEachIndexed { place, setting ->
            ProviderSetting(forecast, info, setting, keys != null, edits,
                rememberFieldFocus(problems, 10 * (index + 1) + place,
                    ForecastForm.Target.Setting(info.id(), setting.name()) in shown),
                shown[ForecastForm.Target.Setting(info.id(), setting.name())],
                last = index == providers.lastIndex && place == info.settings().lastIndex)
        }
    }
}

/** Check connection for the primary or the fallback, and what it came to, in a live region. */
@Composable
private fun CheckConnection(session: SettingsSession, role: SettingsSession.Role, check: SetUpCheck,
    provider: ProviderId) {
    val state by check.state.collectAsStateWithLifecycle()
    val name = session.providerName(provider)
    AppOutlinedButton(onClick = { session.checkConnection(role) }, enabled = state != SetUpCheck.State.Asking) {
        Text(stringResource(R.string.forecast_check, name))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        when (val now = state) {
            SetUpCheck.State.NotAsked -> Unit
            SetUpCheck.State.Asking -> Waiting(stringResource(R.string.check_asking, name))
            is SetUpCheck.State.Works -> Text(pluralStringResource(R.plurals.check_works, now.days, name, now.days))
            is SetUpCheck.State.Failed -> {
                Text(stringResource(R.string.forecast_check_failed))
                EnglishDetails(now.detail)
            }
        }
    }
}

/** One setting of a provider: an API key masked, any other as text, each named with its provider. */
@Composable
private fun ProviderSetting(forecast: ForecastForm, info: ProviderInfo, setting: SettingSpec, keysRead: Boolean,
    edits: Edits, focus: FocusRequester, problem: FieldProblem?, last: Boolean) {
    val label = stringResource(R.string.provider_setting_of, settingLabel(setting), info.name())
    val hint = settingHint(info, setting)
    val text = edits.shown { forecast.text(info.id(), setting.name()) }
    val onEdit = { typed: String -> edits.make { forecast.edit(info.id(), setting.name(), typed) } }
    val imeAction = if (last) ImeAction.Done else ImeAction.Next
    if (setting.secret()) {
        KeyField(label, hint, text, onEdit, problem, keysRead, info.name(), focus, imeAction)
    } else {
        EntryField(label, hint, text, onEdit,
            if (setting.format() == SettingFormat.SERVER_ADDRESS) FieldKind.ADDRESS else FieldKind.TEXT, problem,
            false, focus, imeAction = imeAction)
    }
}

/**
 * An API key: masked until Show, on a keyboard that neither suggests nor learns it, off while the stored keys are
 * read. Its text lives only in the form's memory.
 */
@Composable
private fun KeyField(label: String, hint: String, text: String, onEdit: (String) -> Unit, problem: FieldProblem?,
    enabled: Boolean, provider: String, focus: FocusRequester, imeAction: ImeAction) {
    // not saved: after the system ended the app, as after turning the phone, the key comes back masked
    var visible by remember { mutableStateOf(false) }
    val problemSentence = problem?.let { problemText(it) }
    val toggle = stringResource(if (visible) R.string.key_hide_of else R.string.key_show_of, provider)
    OutlinedTextField(
        value = text,
        onValueChange = onEdit,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = problem != null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false,
            imeAction = imeAction),
        trailingIcon = {
            AppTextButton(onClick = { visible = !visible },
                modifier = Modifier.semantics { contentDescription = toggle }) {
                Text(stringResource(if (visible) R.string.key_hide else R.string.key_show))
            }
        },
        supportingText = {
            Column {
                if (problemSentence != null) {
                    Text(problemSentence)
                }
                Text(hint)
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics {
            if (problemSentence != null) {
                error(problemSentence)
            }
        },
    )
}

/** A setting's name in the app's language, for the settings this version knows; the engine's English otherwise. */
@Composable
private fun settingLabel(setting: SettingSpec): String = when (setting.name()) {
    "api_key" -> stringResource(R.string.provider_api_key)
    "base_url" -> stringResource(R.string.provider_base_url)
    else -> setting.label()
}

/** What to fill in, in the app's language, for the settings this version knows; the engine's English otherwise. */
@Composable
private fun settingHint(info: ProviderInfo, setting: SettingSpec): String =
    when (info.id().value() + "/" + setting.name()) {
        "open-meteo/api_key" -> stringResource(R.string.provider_open_meteo_api_key_hint)
        "open-meteo/base_url" -> stringResource(R.string.provider_open_meteo_base_url_hint)
        "forecast-solar/api_key" -> stringResource(R.string.provider_forecast_solar_api_key_hint)
        else -> setting.hint()
    }
