package nl.enadeag.carchargeplanner.android

import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.time.Clock
import javax.xml.parsers.DocumentBuilderFactory
import nl.enadeag.carchargeplanner.android.prefs.PhoneStoreFiles
import nl.enadeag.carchargeplanner.android.secrets.EncryptedKeychain
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Element

/**
 * What may leave the phone (#397): no cloud backup on any version, and in the transfer to the owner's next phone only
 * the files the rules name, the same list on Android 10 and 11 as on 12 and later. Before Android 12 each of them
 * requires the device-to-device flag, so a backup without it takes nothing. The list holds every file the engine
 * writes in its folder but the forecast cache, and the phone's settings store, and nothing secret, temporary or set
 * aside as damaged. These tests read the rules; `tools/android-backup-check.sh` shows on the emulator that a backup
 * without the flag saves nothing.
 */
class DataExtractionRulesTest {

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** Every domain the rules know, from Android's documentation of both formats. */
        val DOMAINS = listOf("root", "file", "database", "sharedpref", "external", "device_root", "device_file",
            "device_database", "device_sharedpref")

        /** DataStore keeps a store in files/datastore/<name>.preferences_pb. */
        fun storeFile(name: String) = "file:datastore/$name.preferences_pb"
    }

    @TempDir
    lateinit var root: Path

    // the local tests run in the app module's folder
    private val rules = xml("src/main/res/xml/data_extraction_rules.xml")

    private val olderRules = xml("src/main/res/xml/backup_rules.xml")

    private val application = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        .getElementsByTagName("application").item(0) as Element

    /** The phone's preferences stores that stay on this phone. */
    private val storesLeftOut = setOf(PhoneStoreFiles.STATE)

    /** What the engine keeps in its folder that stays on this phone, with why. */
    private val engineFilesLeftOut = mapOf(
        "forecasts" to "the next phone fetches its own forecast, and the folder holds the cache's damaged and " +
            "temporary copies")

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement

    private fun children(parent: Element): List<Element> =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>()

    private fun section(kind: String): Element = children(rules).single { it.tagName == kind }

    private fun named(rule: Element) = "${rule.getAttribute("domain")}:${rule.getAttribute("path")}"

    private fun shown(rule: Element) = "${rule.tagName} ${named(rule)}"

    private fun includes(parent: Element): Set<String> =
        children(parent).filter { it.tagName == "include" }.map(::named).toSet()

    private val transferred by lazy { includes(section("device-transfer")) }

    /** The names the engine gives the files of its data folder, as it makes its parts in the app. */
    private fun namesTheEngineGivesItsFiles(): Set<String> {
        val folder = root.resolve("files")
        val asked = mutableSetOf<String>()
        val watched = Proxy.newProxyInstance(Path::class.java.classLoader, arrayOf(Path::class.java)) { _, method,
            args ->
            if (method.name == "resolve" && args?.size == 1) {
                asked += args[0].toString()
            }
            try {
                method.invoke(folder, *(args ?: emptyArray()))
            } catch (thrown: InvocationTargetException) {
                throw thrown.targetException
            }
        } as Path
        val keys = testPlatform(folder.toFile(), root.resolve("no_backup").toFile()).apiKeys()
        AppEngine.build(watched, { throw IOException("no network in this test") }, PrivateFiles(FilePrivacy.NONE),
            keys, Notifier { }, Clock.systemUTC())
        return asked
    }

    @Test
    fun theManifestTurnsBackupOnWithBothRuleFilesAndNoAgentOfItsOwn() {
        assertThat(application.getAttributeNS(ANDROID, "allowBackup")).isEqualTo("true")
        assertThat(application.getAttributeNS(ANDROID, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
        assertThat(application.getAttributeNS(ANDROID, "fullBackupContent")).isEqualTo("@xml/backup_rules")
        assertThat(application.hasAttributeNS(ANDROID, "backupAgent")).isFalse()
        assertThat(application.hasAttributeNS(ANDROID, "fullBackupOnly")).isFalse()
    }

    @Test
    fun beforeAndroid12EveryRuleIsAnIncludeThatRequiresTheDeviceToDeviceFlag() {
        val older = children(olderRules)

        val flagged = { rule: Element ->
            rule.tagName == "include" && rule.getAttribute("requireFlags") == "deviceToDeviceTransfer"
        }

        assertThat(older).isNotEmpty()
        assertThat(older.filterNot(flagged).map(::shown)).`as`("rules without the device-to-device flag").isEmpty()
    }

    @Test
    fun bothVersionsTransferTheSameNamedFilesAndNothingElse() {
        assertThat(children(section("device-transfer")).filterNot { it.tagName == "include" }.map(::shown))
            .`as`("rules of the transfer that are not an include").isEmpty()

        assertThat(includes(olderRules)).isEqualTo(transferred)
    }

    @Test
    fun fromAndroid12CloudBackupExcludesEveryDomainWhole() {
        val cloud = section("cloud-backup")

        assertThat(cloud.attributes.length).isZero()
        assertThat(children(cloud).map(::shown)).containsExactlyInAnyOrderElementsOf(DOMAINS.map { "exclude $it:." })
    }

    @Test
    fun theTransferTakesNothingSecretTemporaryOrSetAsideAndNotTheNotificationsState() {
        assertThat(transferred).allSatisfy { file ->
            assertThat(file).startsWith("file:")
            assertThat(file).doesNotContain("no_backup", EncryptedKeychain.FILE_NAME, ".tmp", ".damaged",
                storeFile(PhoneStoreFiles.STATE))
        }
    }

    @Test
    fun theTransferNamesEveryFileTheEngineWritesInItsFolderButTheForecastCache() {
        val engineFiles = transferred.filterNot { it.startsWith("file:datastore/") }.map { it.removePrefix("file:") }
        val named = namesTheEngineGivesItsFiles()

        assertThat(named).containsAll(engineFilesLeftOut.keys)
        assertThat(engineFiles).containsExactlyInAnyOrderElementsOf(named - engineFilesLeftOut.keys)
    }

    @Test
    fun everyPreferencesStoreOfThePhoneIsTransferredOrLeftOutOnPurpose() {
        val stores = PhoneStoreFiles::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }

        assertThat(stores).contains(PhoneStoreFiles.SETTINGS, PhoneStoreFiles.STATE)
        assertThat(transferred.filter { it.startsWith("file:datastore/") })
            .containsExactlyInAnyOrderElementsOf((stores - storesLeftOut).map(::storeFile))
    }

    @Test
    fun theRulesHaveNoOtherSections() {
        assertThat(children(rules).map { it.tagName }).containsExactlyInAnyOrder("cloud-backup", "device-transfer")
    }
}
