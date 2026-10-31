package nl.enadeag.carchargeplanner.android

import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.FilePrivacyContractTest

/**
 * The engine's [FilePrivacy.NONE] against its contract, in the app's local tests: it shows that a contract test the
 * app extends runs here, with its inherited tests. Android's app storage is private already, so the app needs no
 * other file privacy; the app's storage (#273) may replace this test with its own.
 */
class NoPrivacyContractTest : FilePrivacyContractTest() {

    override fun privacy(): FilePrivacy = FilePrivacy.NONE
}
