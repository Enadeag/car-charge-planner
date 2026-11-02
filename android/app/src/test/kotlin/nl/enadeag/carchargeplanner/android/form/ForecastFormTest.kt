package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.util.Optional
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.store.StoredKeys
import nl.enadeag.carchargeplanner.engine.config.ForecastSelection
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ForecastFormTest {

    private companion object {
        val OPEN_METEO = ProviderId("open-meteo")
        val FORECAST_SOLAR = ProviderId("forecast-solar")
        const val KEY = "MADEUPKEY123"
        val NO_NETWORK = HttpGet { throw IOException("no network in this test") }
    }

    private val handle = SavedStateHandle()
    private val providers = AppEngine.providers(NO_NETWORK).let { known ->
        known.ids().map { known.find(it).orElseThrow().info() }
    }
    private val form = ForecastForm(handle, providers)

    @Test
    fun theChoicesAndSettingsAreShownAndGivenBackUnchanged() {
        form.load(DRAFT)

        assertThat(form.primary()).isEqualTo(OPEN_METEO)
        assertThat(form.fallback()).isNull()
        assertThat(form.read(DRAFT)).isEqualTo(DRAFT)
    }

    @Test
    fun theFallbackMayNotBeThePrimary() {
        form.load(DRAFT)

        form.chooseFallback(OPEN_METEO)

        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ForecastForm.Target.Fallback to FieldProblem.Refused(InvalidSetting.Rule.SAME_AS_PRIMARY)))

        form.chooseFallback(FORECAST_SOLAR)
        assertThat(form.problems.value).isEmpty()
        assertThat(form.check(DRAFT)!!.forecast().fallback()).contains(FORECAST_SOLAR)
    }

    @Test
    fun aServerAddressAKeyMayNotBeSentToAndAKeyWithOtherCharactersAreRefused() {
        form.load(DRAFT)
        form.chooseFallback(FORECAST_SOLAR)
        form.edit(OPEN_METEO, "base_url", "http://example.com")
        form.edit(FORECAST_SOLAR, "api_key", "not a key!")

        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyInAnyOrderEntriesOf(mapOf(
            ForecastForm.Target.Setting(OPEN_METEO, "base_url") to FieldProblem.ServerAddress,
            ForecastForm.Target.Setting(FORECAST_SOLAR, "api_key") to FieldProblem.LettersAndDigits,
        ))
    }

    @Test
    fun aServerAddressIsKeptWithTheSettingsAndAKeyGoesToTheKeysOnly() {
        form.load(DRAFT)
        form.loadKeys(StoredKeys.Read(Secrets.NONE))
        form.edit(OPEN_METEO, "base_url", "https://meteo.example.com")
        form.edit(OPEN_METEO, "api_key", KEY)

        val forecast = form.check(DRAFT)!!.forecast()
        assertThat(forecast).isEqualTo(ForecastSelection(OPEN_METEO, Optional.empty(),
            mapOf(OPEN_METEO to mapOf("base_url" to "https://meteo.example.com"))))
        assertThat(form.keyChanges().entered()).isEqualTo(Secrets(mapOf(OPEN_METEO to mapOf("api_key" to KEY))))
    }

    @Test
    fun anUntouchedKeyStaysAndAnEmptiedOneIsRemoved() {
        form.load(DRAFT)
        val stored = Secrets(mapOf(OPEN_METEO to mapOf("api_key" to KEY), FORECAST_SOLAR to mapOf("api_key" to "ABC")))
        form.loadKeys(StoredKeys.Read(stored))
        assertThat(form.text(OPEN_METEO, "api_key")).isEqualTo(KEY)
        assertThat(form.keyChanges().isEmpty).isTrue()

        form.edit(FORECAST_SOLAR, "api_key", "")

        val changes = form.keyChanges()
        assertThat(changes.entered()).isEqualTo(Secrets.NONE)
        assertThat(changes.removed()).isEqualTo(mapOf(FORECAST_SOLAR to setOf("api_key")))
    }

    @Test
    fun keysThatCouldNotBeReadCanBeEnteredAgainAndAnEmptyOneRemovesNothing() {
        form.load(DRAFT)
        form.loadKeys(StoredKeys.Unreadable)
        assertThat(form.keysReadable()).isFalse()
        assertThat(form.text(OPEN_METEO, "api_key")).isEmpty()

        form.edit(OPEN_METEO, "api_key", KEY)
        form.edit(FORECAST_SOLAR, "api_key", "")

        assertThat(form.keyChanges().entered()).isEqualTo(Secrets(mapOf(OPEN_METEO to mapOf("api_key" to KEY))))
        assertThat(form.keyChanges().removed()).isEmpty()
    }

    @Test
    fun aTypedKeyNeverReachesTheSavedStateAndIsGoneAfterTheSystemEndedTheApp() {
        form.load(DRAFT)
        form.loadKeys(StoredKeys.Read(Secrets.NONE))
        form.chooseFallback(FORECAST_SOLAR)
        form.edit(FORECAST_SOLAR, "api_key", KEY)

        assertThat(handle.keys().map { handle.get<Any>(it).toString() }).noneMatch { it.contains(KEY) }
        val again = ForecastForm(handle.afterTheSystemEndedTheApp(), providers)
        assertThat(again.fallback()).isEqualTo(FORECAST_SOLAR)
        assertThat(again.text(FORECAST_SOLAR, "api_key")).isEmpty()
        assertThat(again.keyChanges().isEmpty).isTrue()
    }
}
