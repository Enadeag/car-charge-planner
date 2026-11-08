package nl.enadeag.carchargeplanner.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The app's platform parts on the device, the one set its Application keeps, in its own folders. Nothing is written. */
@RunWith(AndroidJUnit4::class)
class AndroidPlatformOnTheDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()

    @Test
    fun theEngineFilesGoInTheFilesFolderAndTheKeychainInTheFolderThatIsNeverBackedUp() {
        val platform = context.platform

        assertEquals(context.filesDir.toPath(), platform.dataFolder())
        assertEquals(File(context.noBackupFilesDir, "keychain.json").toPath(), platform.keychainFile())
    }

    /** The backup rules name the keychain's file as `no_backup/keychain.json` in the app's data folder. */
    @Test
    fun theFolderThatIsNeverBackedUpIsNoBackupInTheAppsDataFolder() {
        assertEquals(File(context.dataDir, "no_backup"), context.noBackupFilesDir)
    }
}
