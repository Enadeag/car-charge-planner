package nl.enadeag.carchargeplanner.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** The app's platform parts on the device: one set per process, in the app's own folders. Nothing is written. */
@RunWith(AndroidJUnit4::class)
class AndroidPlatformOnTheDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyCallGivesTheSameParts() {
        assertSame(AndroidPlatform.of(context), AndroidPlatform.of(context))
    }

    @Test
    fun theEngineFilesGoInTheFilesFolderAndTheKeychainInTheFolderThatIsNeverBackedUp() {
        val platform = AndroidPlatform.of(context)

        assertEquals(context.filesDir.toPath(), platform.dataFolder())
        assertEquals(File(context.noBackupFilesDir, "keychain.json").toPath(), platform.keychainFile())
    }

    /** The backup rules name the keychain's file as `no_backup/keychain.json` in the app's data folder. */
    @Test
    fun theFolderThatIsNeverBackedUpIsNoBackupInTheAppsDataFolder() {
        assertEquals(File(context.dataDir, "no_backup"), context.noBackupFilesDir)
    }
}
