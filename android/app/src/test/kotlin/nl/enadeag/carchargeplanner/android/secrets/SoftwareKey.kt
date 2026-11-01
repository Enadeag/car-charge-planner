package nl.enadeag.carchargeplanner.android.secrets

import java.security.GeneralSecurityException
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The keychain's key in memory, as the Keystore's would be on the phone: an AES-256 key the JVM makes. A test can take
 * it away, put another in its place, or have the next calls to [find] fail.
 */
class SoftwareKey : KeychainKey {

    @Volatile
    var key: SecretKey? = null

    /** How many keys [make] has made. */
    val made = AtomicInteger()

    /** What the next [find] calls throw, while [failuresLeft] is above zero; any exception, runtime ones too. */
    @Volatile
    var failure: Exception? = null

    @Volatile
    var failuresLeft = Int.MAX_VALUE

    /** Which failures count as a key lost for good. */
    @Volatile
    var lostForGood: (GeneralSecurityException) -> Boolean = { false }

    /** Has the next [times] calls to [find] throw [failure]. */
    fun failNext(failure: Exception, times: Int = Int.MAX_VALUE) {
        this.failure = failure
        failuresLeft = times
    }

    override fun find(): Optional<SecretKey> {
        val failing = failure
        if (failing != null && failuresLeft > 0) {
            failuresLeft--
            throw failing
        }
        return Optional.ofNullable(key)
    }

    override fun make(): SecretKey {
        val made = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        key = made
        failure = null
        this.made.incrementAndGet()
        return made
    }

    override fun forget() {
        key = null
        failure = null
    }

    override fun lostForGood(failure: GeneralSecurityException): Boolean = lostForGood.invoke(failure)
}
