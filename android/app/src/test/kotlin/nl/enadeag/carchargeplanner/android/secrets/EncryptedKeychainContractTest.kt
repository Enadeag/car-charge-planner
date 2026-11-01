package nl.enadeag.carchargeplanner.android.secrets

import java.nio.file.Path
import nl.enadeag.carchargeplanner.engine.secrets.Keychain
import nl.enadeag.carchargeplanner.engine.secrets.KeychainContractTest
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.junit.jupiter.api.io.TempDir

/**
 * The Android keychain against the engine's contract, on the JVM with a key in memory: the same store logic as on the
 * phone, where only the key is the Keystore's (`KeystoreOnTheDeviceTest`).
 */
class EncryptedKeychainContractTest : KeychainContractTest() {

    @TempDir
    lateinit var folder: Path

    private val keychain by lazy {
        EncryptedKeychain(folder.resolve("no_backup").resolve(EncryptedKeychain.FILE_NAME),
            PrivateFiles(FilePrivacy.NONE), SoftwareKey())
    }

    override fun keychain(): Keychain = keychain
}
