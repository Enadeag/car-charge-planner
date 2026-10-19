package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.MemoryKeychain;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A keychain whose reads wait until the test lets them go, as a Secret Service that takes its time. */
public final class SlowKeychain extends MemoryKeychain {

    /** Counted down by the test to let the reads go. */
    public final CountDownLatch letGo = new CountDownLatch(1);

    @Override
    public Optional<String> read(String name) throws KeychainException {
        try {
            if (!letGo.await(10, TimeUnit.SECONDS)) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "the test never let the read go");
            }
        } catch (InterruptedException e) {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "interrupted", e);
        }
        return super.read(name);
    }
}
