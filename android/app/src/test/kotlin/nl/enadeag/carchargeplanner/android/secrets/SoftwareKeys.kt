package nl.enadeag.carchargeplanner.android.secrets

import java.security.GeneralSecurityException
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The keychain's keys in memory, as the Keystore's would be on the phone: AES-256 keys the JVM makes, per alias. A test
 * can take a key away, put another in its place, or have the next lookups or the next key made fail.
 */
class SoftwareKeys : KeychainKeys {

    val keys = ConcurrentHashMap<String, SecretKey>()

    /** How many keys [make] has made. */
    val made = AtomicInteger()

    /** How many times [find] was asked. */
    val finds = AtomicInteger()

    /** Which failures count as a key lost for good. */
    @Volatile
    var lostForGood: (GeneralSecurityException) -> Boolean = { false }

    /** When set, [make] throws it instead of making a key. */
    @Volatile
    var makeFailure: Exception? = null

    /** When set, [make] keeps this key under the alias instead of a new one. */
    @Volatile
    var nextMade: SecretKey? = null

    @Volatile
    private var failure: Exception? = null

    @Volatile
    private var failuresLeft = 0

    @Volatile
    private var missingLeft = 0

    /** What [find] throws for one alias, every time. */
    private val failing = ConcurrentHashMap<String, Exception>()

    /** Has the next [times] calls to [find] throw [failure]; any exception, runtime ones too. */
    fun failNext(failure: Exception, times: Int = Int.MAX_VALUE) {
        this.failure = failure
        failuresLeft = times
    }

    /** Has every call to [find] for [alias] throw [failure]. */
    fun fail(alias: String, failure: Exception) {
        failing[alias] = failure
    }

    /** Has the next [times] calls to [find] see no key, as a Keystore that is restarting may. */
    fun missNext(times: Int) {
        missingLeft = times
    }

    /** Lets every lookup find what there is again. */
    fun stopFailing() {
        failure = null
        failuresLeft = 0
        missingLeft = 0
        failing.clear()
    }

    /** The key under [alias]; there must be one. */
    fun key(alias: String): SecretKey = keys.getValue(alias)

    override fun find(alias: String): Optional<SecretKey> {
        finds.incrementAndGet()
        val next = failure
        if (next != null && failuresLeft > 0) {
            failuresLeft--
            throw next
        }
        failing[alias]?.let { throw it }
        if (missingLeft > 0) {
            missingLeft--
            return Optional.empty()
        }
        return Optional.ofNullable(keys[alias])
    }

    override fun make(alias: String): SecretKey {
        makeFailure?.let { throw it }
        check(!keys.containsKey(alias)) { "a key is never replaced, but $alias holds one" }
        val made = nextMade ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keys[alias] = made
        this.made.incrementAndGet()
        return made
    }

    override fun lostForGood(failure: GeneralSecurityException): Boolean = lostForGood.invoke(failure)
}
