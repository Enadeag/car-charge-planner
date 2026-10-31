package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the owner changed in the API keys: the values they entered and the ones they emptied, by setting name. A save
 * applies them to what is stored, so every other stored key stays as it is, also one the app could not read, such as
 * from a locked keychain.
 *
 * @param entered the values entered, new or changed; empty values are left out
 * @param removed per provider, the setting names whose stored value the owner emptied
 */
public record KeyChanges(Secrets entered, Map<ProviderId, Set<String>> removed) {

    /** No change at all. */
    public static final KeyChanges NONE = new KeyChanges(Secrets.NONE, Map.of());

    /**
     * Copies the removals, without providers that remove nothing.
     *
     * @throws IllegalArgumentException when a setting is both entered and removed
     */
    public KeyChanges {
        var kept = new LinkedHashMap<ProviderId, Set<String>>();
        removed.forEach((id, names) -> {
            if (!names.isEmpty()) {
                kept.put(id, Set.copyOf(names));
            }
        });
        for (var provider : kept.entrySet()) {
            for (var name : provider.getValue()) {
                if (entered.of(provider.getKey()).containsKey(name)) {
                    throw new IllegalArgumentException("a setting cannot be both entered and removed: "
                            + provider.getKey().value() + "/" + name);
                }
            }
        }
        removed = Map.copyOf(kept);
    }

    /**
     * What turns the stored keys into the ones shown: values that differ or are new are entered, and stored ones that
     * are no longer shown are removed. A key that was not stored, or not read, is never removed.
     *
     * @param stored the keys as they were read, which the owner saw; none when they could not be read
     * @param shown the keys as they are now: the stored ones with what the owner entered laid over them
     * @return the changes; {@link #isEmpty()} when nothing differs
     */
    public static KeyChanges between(Secrets stored, Secrets shown) {
        var entered = new LinkedHashMap<ProviderId, Map<String, String>>();
        shown.byProvider().forEach((id, values) -> values.forEach((name, value) -> {
            if (!value.equals(stored.of(id).get(name))) {
                entered.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(name, value);
            }
        }));
        var removed = new LinkedHashMap<ProviderId, Set<String>>();
        stored.byProvider().forEach((id, values) -> {
            var gone = new TreeSet<>(values.keySet());
            gone.removeAll(shown.of(id).keySet());
            removed.put(id, gone);
        });
        return new KeyChanges(new Secrets(entered), removed);
    }

    /**
     * Only these values, entered, with nothing removed.
     *
     * @param entered the values to lay over what is stored
     * @return the changes
     */
    public static KeyChanges entering(Secrets entered) {
        return new KeyChanges(entered, Map.of());
    }

    /**
     * Whether nothing changes.
     *
     * @return true when nothing is entered or removed
     */
    public boolean isEmpty() {
        return entered.byProvider().isEmpty() && removed.isEmpty();
    }

    /**
     * The stored keys with these changes: entered values replace or add, removed ones go, and the rest stays.
     *
     * @param stored the keys as they are stored now
     * @return the keys to store
     */
    public Secrets applyTo(Secrets stored) {
        var result = new HashMap<ProviderId, Map<String, String>>();
        stored.byProvider().forEach((id, values) -> result.put(id, new HashMap<>(values)));
        entered.byProvider().forEach((id, values) -> result.computeIfAbsent(id, ignored -> new HashMap<>())
                .putAll(values));
        removed.forEach((id, names) -> {
            var values = result.get(id);
            if (values != null) {
                values.keySet().removeAll(names);
            }
        });
        return new Secrets(result);
    }
}
