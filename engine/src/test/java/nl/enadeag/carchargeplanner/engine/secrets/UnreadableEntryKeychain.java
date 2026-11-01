package nl.enadeag.carchargeplanner.engine.secrets;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A keychain in memory with entries it cannot read, as values a store can no longer decrypt: reading one fails as
 * refused until a new value is written under its name, which it then reads as usual.
 */
public class UnreadableEntryKeychain extends MemoryKeychain {

    /** While true, deleting an entry that cannot be read fails as refused too. */
    public volatile boolean refusesDelete;
    private final Set<String> unreadable = ConcurrentHashMap.newKeySet();

    /**
     * Makes the keychain with no entries; reads of these names are refused until a value is written under them.
     *
     * @param unreadable the names whose entries cannot be read, such as "forecast/forecast-solar/api_key"; a name
     *     given twice counts once
     */
    public UnreadableEntryKeychain(String... unreadable) {
        this.unreadable.addAll(List.of(unreadable));
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        if (unreadable.contains(name)) {
            throw new KeychainException(KeychainException.Kind.REFUSED, "the value cannot be decrypted");
        }
        return super.read(name);
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        super.write(name, value);
        unreadable.remove(name);
    }

    @Override
    public void delete(String name) throws KeychainException {
        if (refusesDelete && unreadable.contains(name)) {
            throw new KeychainException(KeychainException.Kind.REFUSED, "the entry cannot be deleted");
        }
        super.delete(name);
        unreadable.remove(name);
    }
}
