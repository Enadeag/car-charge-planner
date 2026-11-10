package nl.enadeag.carchargeplanner.android.licences

import android.content.res.AssetManager

/** The licence texts of the app and its libraries, as the build put them in the assets, read as UTF-8. */
internal class AssetLicenceFiles(private val assets: AssetManager) : LicenceFiles {
    override fun read(name: String): String =
        assets.open(LicenceIndex.FOLDER + name).use { it.readBytes().toString(Charsets.UTF_8) }
}
