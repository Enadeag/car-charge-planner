package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * What the release build asks of Android and opens to other apps, with what the libraries add: WorkManager's wake lock
 * and network state, which its scheduling and its network condition need, but not its work in the foreground; and
 * besides the app's own activity and boot receiver only the libraries' parts that the system alone reaches. The build
 * hands the merged manifest over as `release.manifest`.
 */
class MergedManifestTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }

    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(requireNotNull(System.getProperty("release.manifest")))).documentElement

    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).let { list ->
        (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.android(attribute: String): String = getAttributeNS(ANDROID, attribute)

    @Test
    fun theAppAsksForTheNetworkTheNotificationsThePhonesStartAndWhatWorkManagerNeeds() {
        val ownReceivers = manifest.getAttribute("package") + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

        assertThat(elements("uses-permission").map { it.android("name") }).containsExactlyInAnyOrder(
            "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.WAKE_LOCK",
            "android.permission.ACCESS_NETWORK_STATE", ownReceivers)
        // androidx.core's, which only the app's own signature holds
        assertThat(elements("permission").map { it.android("name") to it.android("protectionLevel") })
            .containsExactly(ownReceivers to "signature")
    }

    @Test
    fun beyondTheAppsActivityAndBootReceiverOnlyTheSystemReachesWhatIsExported() {
        val exported = listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap(::elements)
            .filter { it.android("exported") == "true" }

        assertThat(exported.map { it.android("name") to it.android("permission") }).containsExactlyInAnyOrder(
            "nl.enadeag.carchargeplanner.android.MainActivity" to "",
            "nl.enadeag.carchargeplanner.android.background.BootReceiver" to "",
            "androidx.work.impl.background.systemjob.SystemJobService" to "android.permission.BIND_JOB_SERVICE",
            "androidx.work.impl.diagnostics.DiagnosticsReceiver" to "android.permission.DUMP",
            "androidx.profileinstaller.ProfileInstallReceiver" to "android.permission.DUMP")
    }

    @Test
    fun workManagerHasNoServiceForWorkInTheForeground() {
        assertThat(elements("service").map { it.android("name") })
            .doesNotContain("androidx.work.impl.foreground.SystemForegroundService")
    }
}
