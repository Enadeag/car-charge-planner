package nl.enadeag.carchargeplanner.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import nl.enadeag.carchargeplanner.android.prefs.WifiOnly
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.setup.SetUpCheck
import nl.enadeag.carchargeplanner.android.ui.components.ChoiceRow
import nl.enadeag.carchargeplanner.android.ui.components.EnglishDetails
import nl.enadeag.carchargeplanner.android.ui.components.EntryField
import nl.enadeag.carchargeplanner.android.ui.components.FieldKind
import nl.enadeag.carchargeplanner.android.ui.components.Fold
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.SectionHeading
import nl.enadeag.carchargeplanner.android.ui.components.problemText
import nl.enadeag.carchargeplanner.android.ui.components.rememberFieldFocus
import nl.enadeag.carchargeplanner.android.ui.components.rememberProblemFold
import nl.enadeag.carchargeplanner.android.ui.setup.Edits
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton
import nl.enadeag.carchargeplanner.android.ui.components.AppTextButton

/**
 * The forecast providers: the primary, the fallback or none, each with Check connection on the values as typed, and a
 * section per provider with its settings, the optional ones folded away. A provider's fold is open when one of them
 * holds a value as the stored keys are read, else closed; a problem in one of them opens it, as [rememberProblemFold]
 * says. An API key is masked, with Show and Hide, filled with the stored key; when the stored keys could not be read,
 * a note says so and a key left empty stays as stored. Last, the "Wi-Fi only" switch of the hourly update while the
 * app is closed, which applies at once, outside the page's Save.
 *
 * @param problems the page's fields in order, for the focus after Save
 */
@Composable
internal fun ForecastPage(session: SettingsSession, edits: Edits, problems: ProblemFocus, wifiOnly: WifiOnly) {
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
    val folds = providers.map { info ->
        key(info.id().value()) {
            val folded = info.settings().filter { it.optional() }
            rememberProblemFold(
                problem = folded.any { ForecastForm.Target.Setting(info.id(), it.name()) in shown },
                startFolded = folded.all { edits.shown { forecast.text(info.id(), it.name()) }.isEmpty() },
                known = keys != null)
        }
    }
    val foldNames = providers.map { foldName(it) }
    val shownSettings = providers.flatMapIndexed { index, info ->
        val (required, optional) = info.settings().partition { !it.optional() }
        (required + if (folds[index].open) optional else emptyList())
            .map { ForecastForm.Target.Setting(info.id(), it.name()) }
    }
    providers.forEachIndexed { index, info ->
        SectionHeading(info.name())
        @Composable
        fun setting(place: Int, spec: SettingSpec) {
            val target = ForecastForm.Target.Setting(info.id(), spec.name())
            ProviderSetting(forecast, info, spec, keys != null, edits,
                rememberFieldFocus(problems, 10 * (index + 1) + place, target in shown), shown[target],
                last = target == shownSettings.lastOrNull())
        }
        val (required, optional) = info.settings().partition { !it.optional() }
        required.forEachIndexed { place, spec -> setting(place, spec) }
        val name = foldNames[index]
        if (name != null) {
            Fold(name, folds[index],
                spoken = if (foldNames.count { it == name } > 1) stringResource(R.string.provider_setting_of, name,
                    info.name()) else null) {
                optional.forEachIndexed { place, spec -> setting(required.size + place, spec) }
            }
        }
    }
    WifiOnlyRow(wifiOnly)
}

/**
 * The name of the fold with a provider's optional settings, by what is in it: "API key and server" for an API key and
 * a server address, "API key" for an API key alone, "More settings" for anything else.
 *
 * @return null for a provider without optional settings, which has no fold
 */
@Composable
private fun foldName(info: ProviderInfo): String? {
    val optional = info.settings().filter { it.optional() }
    val keys = optional.count { it.secret() }
    val servers = optional.count { !it.secret() && it.format() == SettingFormat.SERVER_ADDRESS }
    return when {
        optional.isEmpty() -> null
        keys == 1 && servers == 1 && optional.size == 2 -> stringResource(R.string.provider_fold_key_and_server)
        keys == 1 && optional.size == 1 -> stringResource(R.string.provider_api_key)
        else -> stringResource(R.string.more_settings)
    }
}

/**
 * The "Wi-Fi only" switch with what it does, as one row a screen reader reads as a switch with its label and hint;
 * off until its value is read.
 */
@Composable
private fun WifiOnlyRow(wifiOnly: WifiOnly) {
    val on by wifiOnly.on.collectAsStateWithLifecycle<Boolean?>(null)
    val now = on
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = now == true, enabled = now != null, role = Role.Switch) { wifiOnly.switch(it) }
        .padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(R.string.forecast_wifi_only), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.forecast_wifi_only_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // the row is the control; the switch only shows its state
        Switch(checked = now == true, onCheckedChange = null, enabled = now != null)
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
            focus, imeAction = imeAction)
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
