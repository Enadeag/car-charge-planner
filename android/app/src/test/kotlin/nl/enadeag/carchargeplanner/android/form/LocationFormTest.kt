package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.PlaceName
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocationFormTest {

    private companion object {
        /** The phone's time zone, which set-up's draft has too. */
        val PHONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /** Lisbon's label, as the results show it. */
        const val LISBON = "Lisbon, Portugal"
    }

    private val numbers = NumberText(Locale.ENGLISH)
    private val lisbon = Place("Lisbon", Coordinates(38.7223, -9.1393), ZoneId.of("Europe/Lisbon"), "", "Portugal", "")

    @Test
    fun setUpDoesNotGoOnWithoutAPlaceChosen() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)

        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            LocationForm.Field.PLACE to FieldProblem.NoPlaceChosen))
    }

    @Test
    fun settingsKeepsAValidPlaceEvenTheOneNotChosen() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE })
        form.load(DRAFT)

        assertThat(form.check(DRAFT)).isEqualTo(DRAFT)
    }

    @Test
    fun theTimeZoneStartsAsTheDraftsAndFollowsThePlaceChosen() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        assertThat(form.timeZone()).isEqualTo(ZoneId.of("Europe/Amsterdam"))

        form.choose(lisbon, LISBON)

        val system = form.check(DRAFT)!!.system()
        assertThat(system.location()).isEqualTo(Coordinates(38.7223, -9.1393))
        assertThat(system.timezone()).isEqualTo(ZoneId.of("Europe/Lisbon"))
        assertThat(form.problems.value).isEmpty()
    }

    @Test
    fun typingOneCoordinateTakesNoPlaceChosenAway() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.check(DRAFT)

        form.edit(LocationForm.Field.LATITUDE, "52.37")

        assertThat(form.problems.value).isEmpty()
        form.edit(LocationForm.Field.LONGITUDE, "4.9")
        assertThat(form.check(DRAFT)!!.system().location()).isEqualTo(Coordinates(52.37, 4.9))
    }

    @Test
    fun typedCoordinatesPutTheTimeZoneOfAPlaceBackToThePhones() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(lisbon, LISBON)

        form.edit(LocationForm.Field.LONGITUDE, "-9.2")

        assertThat(form.timeZone()).isEqualTo(PHONE)
        assertThat(form.check(DRAFT)!!.system().timezone()).isEqualTo(PHONE)
    }

    @Test
    fun typedCoordinatesGetThePhonesTimeZoneAsItIsWhenTyped() {
        var phone = PHONE
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { phone })
        form.load(DRAFT)

        phone = ZoneId.of("Europe/Lisbon")
        form.edit(LocationForm.Field.LATITUDE, "38.72")

        assertThat(form.timeZone()).isEqualTo(ZoneId.of("Europe/Lisbon"))
    }

    @Test
    fun aTimeZoneTheOwnerChoseStaysWhenTheyTypeCoordinates() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(ZoneId.of("Asia/Tokyo"))

        form.edit(LocationForm.Field.LATITUDE, "35.68")
        form.edit(LocationForm.Field.LONGITUDE, "139.69")

        assertThat(form.check(DRAFT)!!.system().timezone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun aPlaceChosenAfterAZoneTakesItsZoneAndTypedCoordinatesThenThePhones() {
        val handle = SavedStateHandle()
        val form = LocationForm(handle, numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(ZoneId.of("Asia/Tokyo"))
        form.choose(lisbon, LISBON)
        assertThat(form.timeZone()).isEqualTo(ZoneId.of("Europe/Lisbon"))

        val again = LocationForm(handle.afterTheSystemEndedTheApp(), numbers, placeRequired = true,
            phoneZone = { PHONE })
        again.edit(LocationForm.Field.LATITUDE, "38.8")

        assertThat(again.timeZone()).isEqualTo(PHONE)
    }

    @Test
    fun aZoneChosenSurvivesTheSystemEndingTheApp() {
        val handle = SavedStateHandle()
        val form = LocationForm(handle, numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(ZoneId.of("Asia/Tokyo"))

        val again = LocationForm(handle.afterTheSystemEndedTheApp(), numbers, placeRequired = true,
            phoneZone = { PHONE })
        again.edit(LocationForm.Field.LATITUDE, "35.68")

        assertThat(again.timeZone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun aCoordinateOffTheMapShowsAtItsOwnField() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE })
        form.load(DRAFT)
        form.edit(LocationForm.Field.LONGITUDE, "181")

        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            LocationForm.Field.LONGITUDE to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun theOwnerMayChooseAnotherTimeZone() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE })
        form.load(DRAFT)

        form.choose(ZoneId.of("Asia/Tokyo"))

        assertThat(form.read(DRAFT)!!.system().timezone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
    }

    @Test
    fun inSettingsAStoredZoneCountsAsChosenSoTypedCoordinatesLeaveIt() {
        val stored = DRAFT.withSystem(DRAFT.system().withUnnamedLocation(Coordinates(38.7223, -9.1393),
            ZoneId.of("Europe/Lisbon")), DRAFT.typical())
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE },
            storedZoneIsChosen = true)
        form.load(stored)

        form.edit(LocationForm.Field.LATITUDE, "38.8")

        assertThat(form.timeZone()).isEqualTo(ZoneId.of("Europe/Lisbon"))
        assertThat(form.check(stored)!!.system().timezone()).isEqualTo(ZoneId.of("Europe/Lisbon"))
    }

    @Test
    fun inSettingsAPlaceChosenStillTakesItsZone() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE },
            storedZoneIsChosen = true)
        form.load(DRAFT)

        form.choose(lisbon, LISBON)

        assertThat(form.timeZone()).isEqualTo(ZoneId.of("Europe/Lisbon"))
    }

    @Test
    fun inSetUpAZoneLoadedFromAFileDoesNotCountAsChosen() {
        val stored = DRAFT.withSystem(DRAFT.system().withUnnamedLocation(Coordinates(38.7223, -9.1393),
            ZoneId.of("Europe/Lisbon")), DRAFT.typical())
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(stored)

        form.edit(LocationForm.Field.LATITUDE, "38.8")

        assertThat(form.timeZone()).isEqualTo(PHONE)
    }

    @Test
    fun aChosenPlaceIsSavedWithItsNameAndItsCoordinatesAtFourDecimals() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)

        form.choose(Place("Utrecht", Coordinates(52.09083, 5.12222), PHONE, "Utrecht", "Netherlands", ""),
            "Utrecht, Netherlands")

        assertThat(form.placeName()).isEqualTo(PlaceName("Utrecht, Netherlands"))
        val system = form.check(DRAFT)!!.system()
        assertThat(system.place()).contains(PlaceName("Utrecht, Netherlands"))
        assertThat(system.location()).isEqualTo(Coordinates(52.0908, 5.1222))
    }

    @Test
    fun typingADigitAndPuttingItBackKeepsTheNameAndARealDifferenceDropsIt() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(lisbon, LISBON)

        form.edit(LocationForm.Field.LATITUDE, "38.722")
        assertThat(form.placeName()).isNull()
        form.edit(LocationForm.Field.LATITUDE, "38.72230")
        assertThat(form.placeName()).isEqualTo(PlaceName(LISBON))
        assertThat(form.check(DRAFT)!!.system().place()).contains(PlaceName(LISBON))

        form.edit(LocationForm.Field.LONGITUDE, "-9.1394")
        assertThat(form.placeName()).isNull()
        assertThat(form.check(DRAFT)!!.system().place()).isEmpty()
        form.edit(LocationForm.Field.LONGITUDE, "not a number")
        assertThat(form.placeName()).isNull()
    }

    @Test
    fun aStoredNameStaysWhileTheFieldsAreLeftAloneAndAHandEditedOneIsTrusted() {
        val stored = DRAFT.withSystem(DRAFT.system().withPlace(Coordinates(38.72231, -9.13931),
            ZoneId.of("Europe/Lisbon"), PlaceName("Somewhere else")), DRAFT.typical())
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE },
            storedZoneIsChosen = true)

        form.load(stored)

        assertThat(form.placeName()).isEqualTo(PlaceName("Somewhere else"))
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun aFormWithoutAStoredNameHasNone() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE })

        form.load(DRAFT)

        assertThat(form.placeName()).isNull()
        assertThat(form.read(DRAFT)!!.system().place()).isEmpty()
    }

    @Test
    fun aLabelLongerThanAPlaceNameIsCutAtAHundredCharacters() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)

        form.choose(lisbon, "L".repeat(101))

        assertThat(form.placeName()).isEqualTo(PlaceName("L".repeat(100)))
    }

    @Test
    fun theChosenPlaceSurvivesTheSystemEndingTheApp() {
        val handle = SavedStateHandle()
        val form = LocationForm(handle, numbers, placeRequired = true, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(lisbon, LISBON)

        val again = LocationForm(handle.afterTheSystemEndedTheApp(), numbers, placeRequired = true,
            phoneZone = { PHONE })

        assertThat(again.placeName()).isEqualTo(PlaceName(LISBON))
        assertThat(again.check(DRAFT)!!.system().place()).contains(PlaceName(LISBON))
    }

    @Test
    fun loadingAgainForgetsAPlaceChosenBefore() {
        val form = LocationForm(SavedStateHandle(), numbers, placeRequired = false, phoneZone = { PHONE })
        form.load(DRAFT)
        form.choose(lisbon, LISBON)

        form.load(DRAFT)

        assertThat(form.placeName()).isNull()
    }
}
