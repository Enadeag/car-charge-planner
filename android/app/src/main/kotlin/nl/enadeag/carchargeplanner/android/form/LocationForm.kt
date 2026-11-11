package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.DateTimeException
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.PlaceName
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * Where the home is: its latitude and longitude in degrees, chosen from the place search or typed, and its time
 * zone. The zone follows a place chosen from the search, goes back to the phone's when the owner types a coordinate,
 * and stays as it is once the owner chose a zone themselves, until they choose a place again.
 *
 * The name of the place chosen, or stored with the coordinates, holds while the fields show that place's coordinates
 * as they write them, with four decimals: typing a digit and putting it back keeps it, a real difference drops it.
 *
 * @param handle where the fields' texts, the time zone, whether the owner chose it, and the place's name and
 *     coordinates are kept
 * @param numbers how the screen's language writes numbers
 * @param placeRequired true for set-up, which does not go on without a place chosen; Settings keeps any valid one
 * @param phoneZone the phone's time zone as it is now, which typed coordinates get unless the owner chose a zone
 * @param storedZoneIsChosen true for Settings, where a zone loaded from the settings file counts as chosen by hand, so
 *     typed coordinates leave it alone (#315): the file does not record whether the owner once chose it; false for
 *     set-up, whose draft has the phone's zone
 */
class LocationForm(private val handle: SavedStateHandle, numbers: NumberText, private val placeRequired: Boolean,
    private val phoneZone: () -> ZoneId, private val storedZoneIsChosen: Boolean = false) :
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
        const val ZONE_CHOSEN = "location.zoneChosen"
        const val PLACE_NAME = "location.placeName"
        const val PLACE_LATITUDE = "location.placeLatitude"
        const val PLACE_LONGITUDE = "location.placeLongitude"
    }

    override fun load(config: EngineConfig) {
        val system = config.system()
        state.fill(Field.LATITUDE, numbers.write(system.location().latitude(), DECIMALS))
        state.fill(Field.LONGITUDE, numbers.write(system.location().longitude(), DECIMALS))
        state.fill(Field.TIME_ZONE, system.timezone().id)
        state.fill(Field.PLACE, "")
        handle[ZONE_CHOSEN] = storedZoneIsChosen
        keepPlace(system.place().orElse(null), system.location())
    }

    /**
     * Takes a place the owner chose from the search: its name, its coordinates, and its time zone, also over one they
     * chose.
     *
     * @param place the place chosen
     * @param label the place's label as the results showed it, which becomes its name; cut at
     *     [PlaceName.LONGEST] characters
     */
    fun choose(place: Place, label: String) {
        state.edit(Field.LATITUDE, numbers.write(place.location().latitude(), DECIMALS))
        state.edit(Field.LONGITUDE, numbers.write(place.location().longitude(), DECIMALS))
        state.edit(Field.TIME_ZONE, place.timezone().id)
        state.edit(Field.PLACE, "")
        handle[ZONE_CHOSEN] = false
        keepPlace(PlaceName.fromLabel(label).orElse(null), place.location())
    }

    /**
     * The name of the place chosen, while the fields show its coordinates.
     *
     * @return the name; null when no place was chosen or stored, or a coordinate typed since differs from the
     *     place's at four decimals, or is no number, or a restored value is no longer a valid name or number
     */
    fun placeName(): PlaceName? {
        val latitude = handle.get<String>(PLACE_LATITUDE)?.toDoubleOrNull() ?: return null
        val longitude = handle.get<String>(PLACE_LONGITUDE)?.toDoubleOrNull() ?: return null
        val name = try {
            PlaceName(handle.get<String>(PLACE_NAME) ?: return null)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return if (shows(Field.LATITUDE, latitude) && shows(Field.LONGITUDE, longitude)) name else null
    }

    /**
     * Takes a time zone the owner chose, which typed coordinates then leave as it is.
     *
     * @param zone the home's time zone
     */
    fun choose(zone: ZoneId) {
        state.edit(Field.TIME_ZONE, zone.id)
        handle[ZONE_CHOSEN] = true
    }

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

    /**
     * Puts what the owner typed: the field's problem goes; a typed coordinate takes the "no place chosen" problem
     * away too, and puts the time zone back to the phone's unless the owner chose one.
     *
     * @param field the field
     * @param text what it holds now
     */
    override fun edit(field: Field, text: String) {
        super.edit(field, text)
        if (field == Field.LATITUDE || field == Field.LONGITUDE) {
            state.edit(Field.PLACE, "")
            val phone = phoneZone().id
            if (handle.get<Boolean>(ZONE_CHOSEN) != true && state.text(Field.TIME_ZONE) != phone) {
                state.edit(Field.TIME_ZONE, phone)
            }
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
        val name = placeName()
        val located = if (name == null) stored.withUnnamedLocation(location, zone)
            else stored.withPlace(location, zone, name)
        return config.withSystem(located, config.typical())
    }

    private fun shows(field: Field, degrees: Double): Boolean {
        val typed = numbers.read(state.text(field)) ?: return false
        return numbers.write(typed, DECIMALS) == numbers.write(degrees, DECIMALS)
    }

    private fun keepPlace(name: PlaceName?, location: Coordinates) {
        if (name == null) {
            listOf(PLACE_NAME, PLACE_LATITUDE, PLACE_LONGITUDE).forEach { handle.remove<Any>(it) }
        } else {
            handle[PLACE_NAME] = name.text()
            // as texts, the kind of value the forms keep in the saved state
            handle[PLACE_LATITUDE] = location.latitude().toString()
            handle[PLACE_LONGITUDE] = location.longitude().toString()
        }
    }
}
