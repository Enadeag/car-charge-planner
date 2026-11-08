package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * What the app asks of Android and opens to other apps, from its own manifest: the network, the notifications and the
 * start of the phone; the one activity the launcher starts, single-top so a notification's tap reaches the activity
 * shown, and its alias for those taps, which no other app reaches; the alarm's receiver, which no other app reaches
 * either, and the boot receiver, for the system's broadcasts only.
 */
class ManifestTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val TOOLS = "http://schemas.android.com/tools"
    }

    // the local tests run in the app module's folder
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement

    // what the app takes out of a library's manifest is not asked for; MergedManifestTest reads what is left
    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).let { list ->
        (0 until list.length).map { list.item(it) as Element }.filter { it.getAttributeNS(TOOLS, "node") != "remove" }
    }

    private fun Element.name() = getAttributeNS(ANDROID, "name")

    private fun Element.actions() = getElementsByTagName("action").let { list ->
        (0 until list.length).map { (list.item(it) as Element).name() }
    }

    private val parts = listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap(::elements)

    @Test
    fun theAppAsksForTheNetworkTheNotificationsAndThePhonesStartOnly() {
        assertThat(elements("uses-permission").map { it.name() }).containsExactlyInAnyOrder(
            "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS",
            "android.permission.RECEIVE_BOOT_COMPLETED")
    }

    @Test
    fun theAppHasItsActivityItsAliasAndTheTwoReceiversOfTheWorkInTheBackgroundOnly() {
        assertThat(parts.map { it.name() }).containsExactlyInAnyOrder(".MainActivity", ".NoticeTap",
            ".background.NoticeAlarmReceiver", ".background.BootReceiver")
    }

    @Test
    fun theNotificationsAliasOfTheActivityIsNotExportedAndTakesNoIntentFromOutside() {
        val alias = parts.single { it.tagName == "activity-alias" }

        assertThat(alias.name()).isEqualTo(".NoticeTap")
        assertThat(alias.getAttributeNS(ANDROID, "targetActivity")).isEqualTo(".MainActivity")
        assertThat(alias.getAttributeNS(ANDROID, "exported")).isEqualTo("false")
        assertThat(alias.getElementsByTagName("intent-filter").length).isZero()
    }

    @Test
    fun theAlarmsReceiverIsNotExportedAndTakesNoBroadcast() {
        val alarm = parts.single { it.name() == ".background.NoticeAlarmReceiver" }

        assertThat(alarm.getAttributeNS(ANDROID, "exported")).isEqualTo("false")
        assertThat(alarm.getElementsByTagName("intent-filter").length).isZero()
    }

    @Test
    fun theBootReceiverTakesTheSystemsBroadcastsAfterAStartAndAnUpdateOnly() {
        val boot = parts.single { it.name() == ".background.BootReceiver" }

        assertThat(boot.getAttributeNS(ANDROID, "exported")).isEqualTo("true")
        assertThat(boot.getElementsByTagName("intent-filter").length).isEqualTo(1)
        assertThat(boot.actions()).containsExactlyInAnyOrder("android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED")
        assertThat(boot.getElementsByTagName("category").length + boot.getElementsByTagName("data").length).isZero()
    }

    @Test
    fun theOnlyActivityIsTheLaunchersSingleTopOne() {
        val activity = parts.single { it.tagName == "activity" }

        assertThat(activity.name()).isEqualTo(".MainActivity")
        assertThat(activity.getAttributeNS(ANDROID, "exported")).isEqualTo("true")
        assertThat(activity.getAttributeNS(ANDROID, "launchMode")).isEqualTo("singleTop")
        assertThat(activity.getElementsByTagName("action").item(0).let { it as Element }
            .getAttributeNS(ANDROID, "name")).isEqualTo("android.intent.action.MAIN")
        assertThat(activity.getElementsByTagName("intent-filter").length).isEqualTo(1)
    }
}
