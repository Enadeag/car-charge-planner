package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A keychain in memory, so no test touches the real one. */
public class MemoryKeychain implements Keychain {

    public final Map<String, String> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<String> read(String name) throws KeychainException {
        return Optional.ofNullable(entries.get(name));
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        entries.put(name, value);
    }

    @Override
    public void delete(String name) throws KeychainException {
        entries.remove(name);
    }
}
