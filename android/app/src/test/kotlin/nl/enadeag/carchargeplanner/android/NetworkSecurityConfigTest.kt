package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * The release build's network security config, which no test on a device sees: the instrumented tests run against
 * the debug build, whose own copy lets them reach a server on the device over plain http. It trusts only the
 * system's certificates, Android's default, and not ones a user added (no trust anchors of its own).
 */
class NetworkSecurityConfigTest {

    // the local tests run in the app module's folder
    private val config = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("src/main/res/xml/network_security_config.xml")).documentElement

    private fun elements(): List<Element> {
        val all = config.getElementsByTagName("*")
        return listOf(config) + (0 until all.length).map { all.item(it) as Element }
    }

    @Test
    fun theReleaseBuildRefusesPlainHttpEverywhere() {
        val base = config.getElementsByTagName("base-config").item(0) as Element

        assertThat(base.getAttribute("cleartextTrafficPermitted")).isEqualTo("false")
        assertThat(elements()).noneMatch { it.getAttribute("cleartextTrafficPermitted") == "true" }
    }

    @Test
    fun theReleaseBuildHasNoDebugOverridesOrTrustOfItsOwn() {
        assertThat(elements().map { it.tagName }).doesNotContain("debug-overrides", "trust-anchors")
    }
}
