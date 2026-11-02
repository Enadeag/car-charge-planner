package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.DateTimeException
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * Where the home is: its latitude and longitude in degrees, chosen from the place search or typed, and its time
 * zone, which starts as the phone's and follows a place chosen from the search; the owner may change it.
 *
 * @param handle where the fields' texts and the time zone are kept
 * @param numbers how the screen's language writes numbers
 * @param placeRequired true for set-up, which does not go on without a place chosen; Settings keeps any valid one
 */
class LocationForm(handle: SavedStateHandle, numbers: NumberText, private val placeRequired: Boolean) :
    FieldsForm<LocationForm.Field>(FormState(handle, "location", Field.entries), numbers) {

    /** A field of the form. */
    enum class Field {
        /** The latitude, in degrees, north positive. */
        LATITUDE,
        /** The longitude, in degrees, east positive. */
        LONGITUDE,
        /** The time zone, chosen rather than typed, such as Europe/Amsterdam. */
        TIME_ZONE,
        /** The place as a whole, which shows that none was chosen. */
        PLACE,
    }

    private companion object {
        const val DECIMALS = 4
    }

    override fun load(config: EngineConfig) {
        val system = config.system()
        state.fill(Field.LATITUDE, numbers.write(system.location().latitude(), DECIMALS))
        state.fill(Field.LONGITUDE, numbers.write(system.location().longitude(), DECIMALS))
        state.fill(Field.TIME_ZONE, system.timezone().id)
        state.fill(Field.PLACE, "")
    }

    /**
     * Takes a place the owner chose from the search: its coordinates, and its time zone.
     *
     * @param place the place chosen
     */
    fun choose(place: Place) {
        state.edit(Field.LATITUDE, numbers.write(place.location().latitude(), DECIMALS))
        state.edit(Field.LONGITUDE, numbers.write(place.location().longitude(), DECIMALS))
        choose(place.timezone())
        state.edit(Field.PLACE, "")
    }

    /**
     * Takes a time zone the owner chose.
     *
     * @param zone the home's time zone
     */
    fun choose(zone: ZoneId) = state.edit(Field.TIME_ZONE, zone.id)

    /**
     * The time zone as chosen.
     *
     * @return the zone; null before the form was loaded
     */
    fun timeZone(): ZoneId? = try {
        ZoneId.of(state.text(Field.TIME_ZONE))
    } catch (ignored: DateTimeException) {
        null
    }

    override fun edit(field: Field, text: String) {
        super.edit(field, text)
        if (field == Field.LATITUDE || field == Field.LONGITUDE) {
            state.edit(Field.PLACE, "")
        }
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val stored = config.system()
        val latitude = reader.number(Field.LATITUDE, stored.location().latitude(), DECIMALS)
        val longitude = reader.number(Field.LONGITUDE, stored.location().longitude(), DECIMALS)
        val zone = timeZone() ?: stored.timezone()
        val location = reader.records({ setting ->
            if (setting == ConfigSetting.LONGITUDE) Field.LONGITUDE else Field.LATITUDE
        }) { Coordinates(latitude, longitude) } ?: return null
        if (placeRequired && !location.isChosen) {
            reader.problems[Field.PLACE] = FieldProblem.NoPlaceChosen
            return null
        }
        return config.withSystem(stored.withLocation(location, zone), config.typical())
    }
}
