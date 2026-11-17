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

    private enum class Field { A, B }

    @Test
    fun aValueOfTheWrongKindIsReadAsMissingAndNotReadable() {
        val handle = SavedStateHandle(mapOf("f.text.A" to 7, "f.edited" to 3, "f.typical" to arrayListOf(1, "B")))
        val form = FormState(handle, "f", Field.entries)

        assertThat(form.isReadable()).isFalse()
        assertThat(form.text(Field.A)).isEmpty()
        assertThat(form.isEdited()).isFalse()
        assertThat(form.typical()).containsExactly(Field.B)
    }

    @Test
    fun valuesOfTheKindTheFormPutsAreReadable() {
        val form = FormState(SavedStateHandle(), "f", Field.entries)
        form.fill(Field.A, "5")
        form.edit(Field.B, "6")
        form.markTypical(setOf(Field.A))

        assertThat(form.isReadable()).isTrue()
        assertThat(form.text(Field.B)).isEqualTo("6")
        assertThat(form.typical()).containsExactly(Field.A)
    }
}
