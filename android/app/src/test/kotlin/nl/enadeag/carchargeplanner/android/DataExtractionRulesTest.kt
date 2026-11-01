package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import nl.enadeag.carchargeplanner.android.secrets.EncryptedKeychain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.w3c.dom.Element

/**
 * What may leave the phone: only the keychain's file stays, as its Keystore key never moves; the settings, trips and
 * cache go along to the owner's next phone. No device test sees a backup or a transfer, so this reads the rules.
 */
class DataExtractionRulesTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }

    // the local tests run in the app module's folder
    private val rules = xml("src/main/res/xml/data_extraction_rules.xml")

    private val olderRules = xml("src/main/res/xml/backup_rules.xml")

    private val keychainFiles = listOf("exclude root:no_backup/${EncryptedKeychain.FILE_NAME}",
        "exclude root:no_backup/${EncryptedKeychain.FILE_NAME}.tmp")

    private val application = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        .getElementsByTagName("application").item(0) as Element

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement

    private fun children(parent: Element): List<Element> =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>()

    // no <include>: what is not excluded goes along
    private fun rulesIn(section: Element): List<String> =
        children(section).map { "${it.tagName} ${it.getAttribute("domain")}:${it.getAttribute("path")}" }

    @Test
    fun theManifestUsesBothRulesAndKeepsBackupOff() {
        assertThat(application.getAttributeNS(ANDROID, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
        assertThat(application.getAttributeNS(ANDROID, "fullBackupContent")).isEqualTo("@xml/backup_rules")
        assertThat(application.getAttributeNS(ANDROID, "allowBackup")).isEqualTo("false")
    }

    @ParameterizedTest
    @ValueSource(strings = ["cloud-backup", "device-transfer"])
    fun onlyTheKeychainsFileStaysOnThePhone(kind: String) {
        val section = rules.getElementsByTagName(kind).item(0) as Element

        assertThat(rulesIn(section)).containsExactlyInAnyOrderElementsOf(keychainFiles)
    }

    @Test
    fun beforeAndroid12OnlyTheKeychainsFileStaysToo() {
        assertThat(rulesIn(olderRules)).containsExactlyInAnyOrderElementsOf(keychainFiles)
    }

    @Test
    fun theRulesHaveNoOtherSections() {
        assertThat(children(rules).map { it.tagName }).containsExactlyInAnyOrder("cloud-backup", "device-transfer")
    }
}
