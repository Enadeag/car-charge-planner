package nl.enadeag.carchargeplanner.android.licences

import java.io.IOException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LicenceIndexTest {

    @Test
    fun anIndexGivesTheCopyrightLineAndThePartsInTheirOrder() {
        val index = LicenceIndex.parse("copyright\tCopyright (C) 2026 Someone\npart\tNOTICE\tCar charge planner " +
            "(GPL-3.0-or-later)\npart\tapache-2.0.txt\tApache License 2.0\n")

        assertThat(index).isEqualTo(LicenceIndex("Copyright (C) 2026 Someone", listOf(
            LicencePart("NOTICE", "Car charge planner (GPL-3.0-or-later)"),
            LicencePart("apache-2.0.txt", "Apache License 2.0"))))
    }

    @ParameterizedTest
    @ValueSource(strings = ["part\tNOTICE\tTitle\n", "copyright\tC\npart\tNOTICE\n", "copyright\tC\nfile\tA\tB\n",
        "copyright\tC\npart\t\tTitle\n"])
    fun anIndexTheBuildNeverWritesCannotBeRead(text: String) {
        assertThatThrownBy { LicenceIndex.parse(text) }.isInstanceOf(IOException::class.java)
    }
}
