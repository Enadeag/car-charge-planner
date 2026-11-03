package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormStateTest {

    @Test
    fun forgetTakesAPrefixAndTheKeysUnderItButNotAKeyThatOnlyStartsAlike() {
        val handle = SavedStateHandle(mapOf("car" to true, "car.text.X" to "75", "carpet" to "red",
            "settings.page" to "SOLAR"))

        handle.forget(listOf("car"))

        assertThat(handle.keys()).containsExactlyInAnyOrder("carpet", "settings.page")
    }
}
