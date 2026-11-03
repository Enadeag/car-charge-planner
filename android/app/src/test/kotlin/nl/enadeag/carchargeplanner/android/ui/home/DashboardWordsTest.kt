package nl.enadeag.carchargeplanner.android.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The dashboard's words that only the drawing shows, so no screen test can read them. */
@RunWith(AndroidJUnit4::class)
class DashboardWordsTest {

    private val words = DashboardWords(ApplicationProvider.getApplicationContext<Context>().resources, Locale.ENGLISH,
        true, ZoneId.of("Europe/Amsterdam"))

    @Test
    fun theScaleSaysThePowerAtTheTopOfTheChartInWholeKilowatts() {
        assertThat(words.scale(4.0)).isEqualTo("4 kW")
    }
}
