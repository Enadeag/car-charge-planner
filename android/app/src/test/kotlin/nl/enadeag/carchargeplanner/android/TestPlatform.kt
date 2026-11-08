package nl.enadeag.carchargeplanner.android

import java.io.File
import java.time.Clock
import nl.enadeag.carchargeplanner.android.secrets.SoftwareKeys

/**
 * The app's platform parts on these folders, with a key in memory for the Keystore's, for a test outside this package.
 *
 * @param files the folder of the engine's files
 * @param noBackup the folder of the keychain's file
 * @return the parts
 */
internal fun testPlatform(files: File, noBackup: File): AndroidPlatform =
    AndroidPlatform(files, noBackup, SoftwareKeys(), Clock.systemUTC())
