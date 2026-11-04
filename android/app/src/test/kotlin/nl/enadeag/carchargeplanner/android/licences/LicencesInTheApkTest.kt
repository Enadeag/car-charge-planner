package nl.enadeag.carchargeplanner.android.licences

import java.io.File
import java.util.zip.ZipFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The licence texts in the debug APK as the build packs it (the build hands its folder over as `apk.dir`). */
class LicencesInTheApkTest {

    private fun apk(): File = File(requireNotNull(System.getProperty("apk.dir"))).listFiles().orEmpty()
        .single { it.name.endsWith(".apk") }

    @Test
    fun theApkHoldsTheIndexTheGplAndTheApacheLicenceThatTheIndexNames() {
        ZipFile(apk()).use { apk ->
            fun asset(name: String): String {
                val entry = requireNotNull(apk.getEntry("assets/" + LicenceIndex.FOLDER + name)) { "no $name" }
                return apk.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val index = LicenceIndex.parse(asset(LicenceIndex.INDEX))

            assertThat(index.copyright).startsWith("Copyright")
            assertThat(index.parts.take(3).map { it.asset }).containsExactly("NOTICE", "LICENSE", "ADDITIONAL-TERMS.md")
            assertThat(index.parts.map { it.title }).contains("Apache License 2.0", "Jackson", "SLF4J")
            assertThat(asset("LICENSE")).contains("GNU GENERAL PUBLIC LICENSE", "Version 3, 29 June 2007")
            assertThat(asset("apache-2.0.txt")).contains("Apache License", "Version 2.0, January 2004",
                "Artifact: androidx.compose.material3:material3-android")
            index.parts.forEach { assertThat(asset(it.asset)).`as`(it.asset).isNotBlank() }
        }
    }
}
