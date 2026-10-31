package nl.enadeag.carchargeplanner.engine.secrets;

/** The in-memory keychain the tests use keeps the same contract as a system's, so a test on it holds there too. */
class MemoryKeychainTest extends KeychainContractTest {

    private final MemoryKeychain keychain = new MemoryKeychain();

    @Override
    protected Keychain keychain() {
        return keychain;
    }
}
