package nl.enadeag.carchargeplanner.android.licences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import org.junit.runner.RunWith

/** The app's own licence texts, as the build put them in its assets, read on Robolectric. */
@RunWith(AndroidJUnit4::class)
class AssetLicenceFilesTest {

    private val files = AssetLicenceFiles(ApplicationProvider.getApplicationContext<Context>().assets)

    @Test
    fun aTextKeepsItsCharactersOutsideAscii() {
        assertThat(files.read("jackson.txt")).contains("Copyright © 2023 Werner Randelshofer", "“AS IS”")
    }

    @Test
    fun aMissingTextCannotBeRead() {
        assertThatThrownBy { files.read("gone.txt") }.isInstanceOf(IOException::class.java)
    }
}
