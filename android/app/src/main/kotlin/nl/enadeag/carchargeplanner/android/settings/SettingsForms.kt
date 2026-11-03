package nl.enadeag.carchargeplanner.android.settings

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.ChargerForm
import nl.enadeag.carchargeplanner.android.form.ConfigForm
import nl.enadeag.carchargeplanner.android.form.ForecastForm
import nl.enadeag.carchargeplanner.android.form.HouseForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.PanelsForm
import nl.enadeag.carchargeplanner.android.form.PlanningForm
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo

/**
 * The forms of Settings' pages, on the screen's saved state. They are made anew each time a page opens, so the problems
 * a page showed before, which live only in the forms, do not come back; their texts are in the handle.
 *
 * @param handle the screen's saved state
 * @param numbers how the screen's language writes numbers
 * @param phoneZone the phone's time zone as it is now
 * @param providers what the known forecast providers tell about themselves, in the order offered
 */
class SettingsForms(handle: SavedStateHandle, numbers: NumberText, phoneZone: () -> ZoneId,
    providers: List<ProviderInfo>) {

    companion object {
        /** The first part of the forms' keys in the handle, which set-up's forms share in part. */
        val PREFIXES = listOf("panels", "location", "car", "house", "charger", "forecast", "away", "planning")
    }

    /** Every panel array. */
    val panels = PanelsForm(handle, numbers)

    /** Where the home is; a zone loaded from the file counts as chosen by hand (#315). */
    val location = LocationForm(handle, numbers, placeRequired = false, phoneZone = phoneZone,
        storedZoneIsChosen = true)

    /** The car. */
    val car = CarForm(handle, numbers)

    /** The house's own use. */
    val house = HouseForm(handle, numbers)

    /** The wall charger. */
    val charger = ChargerForm(handle, numbers)

    /** The forecast providers and their API keys. */
    val forecast = ForecastForm(handle, providers)

    /** The weekly away periods. */
    val away = WeeklyAwayForm(handle)

    /** The planning preferences. */
    val planning = PlanningForm(handle, numbers)

    /**
     * The forms a page shows, in its order.
     *
     * @param page the page
     * @return its forms
     */
    fun of(page: SettingsPage): List<ConfigForm> = when (page) {
        SettingsPage.SOLAR -> listOf(panels)
        SettingsPage.LOCATION -> listOf(location)
        SettingsPage.CAR_AND_HOUSE -> listOf(car, house)
        SettingsPage.CHARGER -> listOf(charger)
        SettingsPage.FORECAST -> listOf(forecast)
        SettingsPage.AWAY -> listOf(away)
        SettingsPage.PLANNING -> listOf(planning)
    }
}
