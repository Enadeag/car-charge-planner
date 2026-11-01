package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;

/**
 * The secrets as keychain entries: one per value, named {@code forecast/<provider>/<name>} after the setting, so other
 * integrations can add their own prefix. A keychain cannot list its entries, so one more entry, {@code index}, holds
 * the names as a JSON list. It is in the keychain, not the config, so that names and values cannot get out of step when the config is
 * copied or deleted; it holds no values.
 */
final class KeychainEntries {

    /** Whether the keychain can be used now. */
    enum State {
        /** It answered: the entries can be read and written. */
        USABLE,
        /** It is there but locked: nothing can be read or written until the owner unlocks it. */
        LOCKED,
        /**
         * There is none or it does not answer; one that refused, such as without a logon session, counts as
         * unavailable too.
         */
        UNAVAILABLE
    }

    /**
     * What the keychain holds, as far as it could be read.
     *
     * @param readable the values of the entries that could be read
     * @param unreadable per provider, the setting names whose entries the keychain refused to give
     */
    record Contents(Secrets readable, Map<ProviderId, Set<String>> unreadable) {

        /**
         * Copies the names.
         *
         * @throws IllegalArgumentException when a setting is both readable and unreadable
         */
        Contents {
            var copied = new HashMap<ProviderId, Set<String>>();
            unreadable.forEach((id, names) -> {
                for (var name : names) {
                    if (readable.of(id).containsKey(name)) {
                        throw new IllegalArgumentException("a setting cannot be both readable and unreadable: "
                                + entryName(id, name));
                    }
                }
                copied.put(id, Set.copyOf(names));
            });
            unreadable = Map.copyOf(copied);
        }

        /**
         * The entries that could not be read and that a save keeps as they are: those it neither writes nor removes.
         *
         * @param toStore the keys the save writes; one of them replaces the entry that could not be read
         * @param removed per provider, the setting names the save removes, also ones that could not be read
         * @return per provider, the setting names to keep unread
         */
        Map<ProviderId, Set<String>> keptBy(Secrets toStore, Map<ProviderId, Set<String>> removed) {
            var kept = new HashMap<ProviderId, Set<String>>();
            unreadable.forEach((id, names) -> {
                var left = new TreeSet<>(names);
                left.removeAll(toStore.of(id).keySet());
                left.removeAll(removed.getOrDefault(id, Set.of()));
                if (!left.isEmpty()) {
                    kept.put(id, Set.copyOf(left));
                }
            });
            return Map.copyOf(kept);
        }
    }

    /** The name of the entry that lists the others. */
    static final String INDEX = "index";
    private static final String PREFIX = "forecast/";

    private final Keychain keychain;

    /**
     * Makes the entries' reader and writer; nothing is read yet.
     *
     * @param keychain the keychain the entries are kept in
     */
    KeychainEntries(Keychain keychain) {
        this.keychain = keychain;
    }

    /**
     * Whether the keychain can be used now, tried by reading the index.
     *
     * @return usable; locked; or unavailable when there is no keychain, it does not answer, or it refused (such as
     *     without a logon session on Windows), which counts as unavailable
     */
    State state() {
        try {
            keychain.read(INDEX);
            return State.USABLE;
        } catch (KeychainException e) {
            return e.kind() == KeychainException.Kind.LOCKED ? State.LOCKED : State.UNAVAILABLE;
        }
    }

    /**
     * Reads every entry the index lists.
     *
     * @return the secrets; none when there is no index
     * @throws KeychainException when the keychain fails or the index is damaged
     */
    Secrets read() throws KeychainException {
        var byProvider = new HashMap<ProviderId, Map<String, String>>();
        for (var name : index()) {
            var parts = name.substring(PREFIX.length()).split("/", 2);
            keychain.read(name).ifPresent(value ->
                    byProvider.computeIfAbsent(new ProviderId(parts[0]), ignored -> new HashMap<>())
                            .put(parts[1], value));
        }
        return new Secrets(byProvider);
    }

    /**
     * Reads every entry the index lists, passing over the ones the keychain refuses to give, such as a value it can no
     * longer decrypt, so a save can keep them.
     *
     * @return the values that could be read, and the names of the entries that could not; none when there is no index
     * @throws KeychainException when the keychain is locked or cannot be used, or the index cannot be read or is
     *     damaged
     */
    Contents contents() throws KeychainException {
        var byProvider = new HashMap<ProviderId, Map<String, String>>();
        var unreadable = new HashMap<ProviderId, Set<String>>();
        for (var name : index()) {
            var parts = name.substring(PREFIX.length()).split("/", 2);
            var id = new ProviderId(parts[0]);
            try {
                keychain.read(name).ifPresent(value ->
                        byProvider.computeIfAbsent(id, ignored -> new HashMap<>()).put(parts[1], value));
            } catch (KeychainException e) {
                if (e.kind() != KeychainException.Kind.REFUSED) {
                    throw e;
                }
                unreadable.computeIfAbsent(id, ignored -> new TreeSet<>()).add(parts[1]);
            }
        }
        return new Contents(new Secrets(byProvider), unreadable);
    }

    /**
     * Replaces everything with {@code secrets}, but for the entries that could not be read and are kept: they stay as
     * they are, and listed. Each value is read back; then the index is written, and entries it no longer lists are
     * removed, so a write that fails part of the way never removes an entry the index lists.
     *
     * @param secrets the secrets to keep, with names that can be split up again
     * @param kept per provider, the setting names of entries that could not be read and stay as they are; none of
     *     them in {@code secrets}
     * @throws KeychainException when the keychain fails or does not keep a value
     * @throws IllegalArgumentException when a kept setting is in {@code secrets} too
     */
    void write(Secrets secrets, Map<ProviderId, Set<String>> kept) throws KeychainException {
        var wanted = new TreeMap<String, String>();
        secrets.byProvider().forEach((id, values) ->
                values.forEach((key, value) -> wanted.put(entryName(id, key), value)));
        var listed = new TreeSet<>(wanted.keySet());
        kept.forEach((id, names) -> names.forEach(name -> {
            if (wanted.containsKey(entryName(id, name))) {
                throw new IllegalArgumentException("a kept setting cannot be written too: " + entryName(id, name));
            }
            listed.add(entryName(id, name));
        }));
        List<String> old;
        try {
            old = index();
        } catch (KeychainException ignored) {
            old = List.of(); // a damaged index: its entries are lost to us, not worth stopping for
        }
        for (var entry : wanted.entrySet()) {
            writeAndCheck(entry.getKey(), entry.getValue());
        }
        var index = StoredJson.array();
        listed.forEach(index::add);
        writeAndCheck(INDEX, StoredJson.compact(index));
        for (var name : old) {
            if (!listed.contains(name)) {
                keychain.delete(name);
            }
        }
    }

    /**
     * The name of a setting's entry.
     *
     * @param id the provider the setting belongs to
     * @param setting the setting's name, such as "api_key"
     * @return the entry's name, such as "forecast/open-meteo/api_key"
     */
    static String entryName(ProviderId id, String setting) {
        return PREFIX + id.value() + "/" + setting;
    }

    private void writeAndCheck(String name, String value) throws KeychainException {
        keychain.write(name, value);
        if (keychain.read(name).filter(value::equals).isEmpty()) {
            throw new KeychainException(KeychainException.Kind.REFUSED,
                    "the keychain did not keep a value that was written to it");
        }
    }

    private List<String> index() throws KeychainException {
        var text = keychain.read(INDEX);
        if (text.isEmpty()) {
            return List.of();
        }
        var names = new ArrayList<String>();
        try {
            var stored = StoredJson.parse(text.get());
            if (!stored.isArray()) {
                throw damaged();
            }
            for (var name : stored.values()) {
                if (!name.isString() || !isEntryName(name.stringValue())) {
                    throw damaged();
                }
                names.add(name.stringValue());
            }
        } catch (JacksonException ignored) {
            throw damaged();
        }
        return names;
    }

    private static KeychainException damaged() {
        return new KeychainException(KeychainException.Kind.REFUSED, "the keychain's index of names is damaged");
    }

    private static boolean isEntryName(String name) {
        if (name == null || !name.startsWith(PREFIX)) {
            return false;
        }
        var end = name.indexOf('/', PREFIX.length());
        return end > 0 && ProviderId.isValid(name.substring(PREFIX.length(), end));
    }
}
