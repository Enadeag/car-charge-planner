package nl.enadeag.carchargeplanner.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import nl.enadeag.carchargeplanner.android.prefs.PhoneStoreFiles
import nl.enadeag.carchargeplanner.android.secrets.EncryptedKeychain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.w3c.dom.Element

/**
 * What may leave the phone: only the keychain's file, as its Keystore key never moves, the notifications' state
 * store, which belongs to this phone, and WorkManager's database, which is made again, stay; the settings, trips, cache and the phone's settings store, with the
 * "Wi-Fi only" switch, go along to the owner's next phone. No device test sees a backup or a transfer, so this reads
 * the rules.
 */
class DataExtractionRulesTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }

    // the local tests run in the app module's folder
    private val rules = xml("src/main/res/xml/data_extraction_rules.xml")

    private val olderRules = xml("src/main/res/xml/backup_rules.xml")

    private val keychainFiles = listOf("exclude root:no_backup/${EncryptedKeychain.FILE_NAME}",
        "exclude root:no_backup/${EncryptedKeychain.FILE_NAME}.tmp",
        "exclude root:no_backup/${EncryptedKeychain.DAMAGED_FILE_NAME}")

    // DataStore keeps a store in files/datastore/<name>.preferences_pb, written through <that>.tmp
    private val stateFiles = listOf("exclude file:datastore/${PhoneStoreFiles.STATE}.preferences_pb",
        "exclude file:datastore/${PhoneStoreFiles.STATE}.preferences_pb.tmp")

    // WorkManager's database, written through SQLite's write-ahead log, which WorkManager makes again
    private val workFiles = listOf("exclude database:androidx.work.workdb", "exclude database:androidx.work.workdb-wal",
        "exclude database:androidx.work.workdb-shm")

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
    fun onlyTheKeychainsFileTheNotificationsStateAndTheWorkDatabaseStayOnThePhone(kind: String) {
        val section = rules.getElementsByTagName(kind).item(0) as Element

        assertThat(rulesIn(section)).containsExactlyInAnyOrderElementsOf(keychainFiles + stateFiles + workFiles)
    }

    @Test
    fun beforeAndroid12OnlyTheKeychainsFileTheNotificationsStateAndTheWorkDatabaseStayToo() {
        assertThat(rulesIn(olderRules)).containsExactlyInAnyOrderElementsOf(keychainFiles + stateFiles + workFiles)
    }

    @Test
    fun thePhonesSettingsStoreGoesAlong() {
        val excluded = (rulesIn(rules.getElementsByTagName("device-transfer").item(0) as Element) + rulesIn(olderRules))

        assertThat(excluded).noneMatch { it.contains(PhoneStoreFiles.SETTINGS) }
    }

    @Test
    fun theRulesHaveNoOtherSections() {
        assertThat(children(rules).map { it.tagName }).containsExactlyInAnyOrder("cloud-backup", "device-transfer")
    }
}
