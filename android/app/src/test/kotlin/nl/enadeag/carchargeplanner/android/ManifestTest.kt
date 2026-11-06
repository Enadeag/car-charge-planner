package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * What the app asks of Android and opens to other apps, from its own manifest: the network and the notifications,
 * and the one activity the launcher starts, single-top so a notification's tap reaches the activity shown.
 */
class ManifestTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }

    // the local tests run in the app module's folder
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement

    private fun elements(tag: String): List<Element> = manifest.getElementsByTagName(tag).let { list ->
        (0 until list.length).map { list.item(it) as Element }
    }

    @Test
    fun theAppAsksForTheNetworkAndTheNotificationsOnly() {
        assertThat(elements("uses-permission").map { it.getAttributeNS(ANDROID, "name") })
            .containsExactlyInAnyOrder("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS")
    }

    @Test
    fun theOnlyPartOpenToOtherAppsIsTheLaunchersSingleTopActivity() {
        val parts = listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap(::elements)

        assertThat(parts.map { it.getAttributeNS(ANDROID, "name") }).containsExactly(".MainActivity")
        val activity = parts.single()
        assertThat(activity.getAttributeNS(ANDROID, "exported")).isEqualTo("true")
        assertThat(activity.getAttributeNS(ANDROID, "launchMode")).isEqualTo("singleTop")
        assertThat(activity.getElementsByTagName("action").item(0).let { it as Element }
            .getAttributeNS(ANDROID, "name")).isEqualTo("android.intent.action.MAIN")
        assertThat(activity.getElementsByTagName("intent-filter").length).isEqualTo(1)
    }
}
