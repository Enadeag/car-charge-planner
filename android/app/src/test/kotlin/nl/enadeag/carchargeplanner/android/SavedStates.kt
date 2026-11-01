package nl.enadeag.carchargeplanner.android

import androidx.lifecycle.SavedStateHandle
import org.assertj.core.api.Assertions.assertThat

/**
 * The handle a screen gets back after the system ended the app: a new one with the values the old one held, which
 * Android carried over in a Bundle. Each value must be of a kind the app keeps there: texts, whole numbers, yes or no,
 * and lists of texts or of whole numbers.
 */
internal fun SavedStateHandle.afterTheSystemEndedTheApp(): SavedStateHandle {
    val values = keys().associateWith { get<Any>(it) }
    values.forEach { (key, value) ->
        val kept = value is String || value is Int || value is Boolean
            || value is ArrayList<*> && value.all { item -> item is String || item is Int }
        assertThat(kept).`as`("$key holds $value, which a Bundle does not keep as it is").isTrue()
    }
    return SavedStateHandle(values)
}
