package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;

/**
 * The secrets as keychain entries: one per value, named {@code forecast/<provider>/<name>} after the setting, so other
 * integrations can add their own prefix. A keychain cannot list its entries, so one more entry, {@code index}, holds
 * the names as a JSON list. It is in the keychain, not the config, so that names and values cannot get out of step
 * when the config is copied or deleted; it holds no values.
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
     * @param listed the names of the entries the index lists, such as "forecast/open-meteo/api_key"
     */
    record Contents(Secrets readable, Map<ProviderId, Set<String>> unreadable, Set<String> listed) {

        /** Nothing read, nothing refused, nothing listed. */
        static final Contents NONE = new Contents(Secrets.NONE, Map.of(), Set.of());

        /**
         * Copies the names, without providers that have none.
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
                if (!names.isEmpty()) {
                    copied.put(id, Set.copyOf(names));
                }
            });
            unreadable = Map.copyOf(copied);
            listed = Set.copyOf(listed);
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
                kept.put(id, left);
            });
            return new Contents(Secrets.NONE, kept, Set.of()).unreadable();
        }

        /**
         * Whether one of these settings is stored, readable or not.
         *
         * @param settings per provider, the setting names asked about
         * @return true when the index lists an entry for one of them that holds a value or could not be read
         */
        boolean holdsAnyOf(Map<ProviderId, Set<String>> settings) {
            for (var setting : settings.entrySet()) {
                for (var name : setting.getValue()) {
                    if (readable.of(setting.getKey()).containsKey(name)
                            || unreadable.getOrDefault(setting.getKey(), Set.of()).contains(name)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Says what was read by name only, never a value.
         *
         * @return the names of the entries that could be read, could not be read, and are listed
         */
        @Override
        public String toString() {
            var read = new TreeSet<String>();
            readable.byProvider().forEach((id, values) -> read.addAll(names(Map.of(id, values.keySet()))));
            return "Contents[readable=" + read + ", unreadable=" + names(unreadable) + ", listed="
                    + new TreeSet<>(listed) + "]";
        }
    }

    /**
     * The index could be read, but is not a list of the engine's names; or, without a file to fall back to, the
     * keychain refused to give it.
     */
    private static final class DamagedIndex extends KeychainException {

        private static final long serialVersionUID = 1L;

        DamagedIndex() {
            super(Kind.REFUSED, "the keychain's index of names is damaged");
        }
    }

    /** The name of the entry that lists the others. */
    static final String INDEX = "index";
    private static final String PREFIX = "forecast/";

    private final Keychain keychain;
    private final KeyFallback fallback;

    /**
     * Makes the entries' reader and writer; nothing is read yet.
     *
     * @param keychain the keychain the entries are kept in
     * @param fallback where the keys go when the keychain cannot be used; without a file, an index the keychain
     *     refuses to give counts as a damaged one, so a save can start a new index
     */
    KeychainEntries(Keychain keychain, KeyFallback fallback) {
        this.keychain = keychain;
        this.fallback = fallback;
    }

    /**
     * Whether the keychain can be used now, tried by reading the index.
     *
     * @return usable; locked; or unavailable when there is no keychain, it does not answer, or it refused (such as
     *     without a logon session on Windows), which counts as unavailable. Without a file to fall back to, an index it
     *     refused counts as a damaged one, so the keychain is usable
     */
    State state() {
        try {
            keychain.read(INDEX);
            return State.USABLE;
        } catch (KeychainException e) {
            if (e.kind() == KeychainException.Kind.LOCKED) {
                return State.LOCKED;
            }
            return damagesTheIndex(e) ? State.USABLE : State.UNAVAILABLE;
        }
    }

    /**
     * Reads every entry the index lists.
     *
     * @return the secrets; none when there is no index
     * @throws KeychainException when the keychain fails, also for one entry, or the index is damaged (also when it
     *     refuses to give the index without a file to fall back to)
     */
    Secrets read() throws KeychainException {
        return entries(index(), true).readable();
    }

    /**
     * Reads every entry the index lists, passing over the ones the keychain refuses to give, such as a value it can no
     * longer decrypt, so a save can keep them. A damaged index counts as none, as its entries cannot be found anyway.
     *
     * @return the values that could be read, the names of the entries that could not, and the names the index lists;
     *     none when there is no index or it is damaged
     * @throws KeychainException when the keychain is locked or cannot be used, or refuses to give the index while
     *     there is a file to fall back to
     */
    Contents contents() throws KeychainException {
        try {
            return entries(index(), false);
        } catch (DamagedIndex ignored) {
            return Contents.NONE;
        }
    }

    /**
     * The names the index lists, without reading the entries; none when it is damaged.
     *
     * @return what the index lists, and no values
     * @throws KeychainException when the keychain is locked or cannot be used, or refuses to give the index while
     *     there is a file to fall back to
     */
    Contents listed() throws KeychainException {
        try {
            return new Contents(Secrets.NONE, Map.of(), Set.copyOf(index()));
        } catch (DamagedIndex ignored) {
            return Contents.NONE;
        }
    }

    /** Reads the named entries; a refused one fails the read when strict, and is passed over and named otherwise. */
    private Contents entries(List<String> names, boolean strict) throws KeychainException {
        var byProvider = new HashMap<ProviderId, Map<String, String>>();
        var unreadable = new HashMap<ProviderId, Set<String>>();
        for (var name : names) {
            var parts = name.substring(PREFIX.length()).split("/", 2);
            var id = new ProviderId(parts[0]);
            try {
                keychain.read(name).ifPresent(value ->
                        byProvider.computeIfAbsent(id, ignored -> new HashMap<>()).put(parts[1], value));
            } catch (KeychainException e) {
                if (strict || e.kind() != KeychainException.Kind.REFUSED) {
                    throw e;
                }
                unreadable.computeIfAbsent(id, ignored -> new TreeSet<>()).add(parts[1]);
            }
        }
        return new Contents(new Secrets(byProvider), unreadable, Set.copyOf(names));
    }

    /**
     * Replaces everything with {@code secrets}, but for the entries that could not be read and are kept: they stay as
     * they are, and listed. Each value is read back; then the index is written, and the entries {@code before} lists
     * that it no longer lists are removed, so a write that fails part of the way never removes an entry the index
     * lists. The index is not read again, so an index that cannot be read now never makes the write remove more.
     *
     * @param secrets the secrets to keep, with names that can be split up again
     * @param keptUnread per provider, the setting names of entries that could not be read and stay as they are; none
     *     of them in {@code secrets}
     * @param before what the index listed when this save read it
     * @throws KeychainException when the keychain fails or does not keep a value
     * @throws IllegalArgumentException when a kept setting is in {@code secrets} too
     */
    void write(Secrets secrets, Map<ProviderId, Set<String>> keptUnread, Contents before) throws KeychainException {
        var wanted = new TreeMap<String, String>();
        secrets.byProvider().forEach((id, values) ->
                values.forEach((key, value) -> wanted.put(entryName(id, key), value)));
        var listed = new TreeSet<>(wanted.keySet());
        for (var name : names(keptUnread)) {
            if (wanted.containsKey(name)) {
                throw new IllegalArgumentException("a kept setting cannot be written too: " + name);
            }
            listed.add(name);
        }
        for (var entry : wanted.entrySet()) {
            writeAndCheck(entry.getKey(), entry.getValue());
        }
        var index = StoredJson.array();
        listed.forEach(index::add);
        writeAndCheck(INDEX, StoredJson.compact(index));
        for (var name : new TreeSet<>(before.listed())) {
            if (!listed.contains(name)) {
                keychain.delete(name);
            }
        }
    }

    /**
     * The names of these settings' entries.
     *
     * @param settings per provider, setting names
     * @return the entries' names, such as "forecast/open-meteo/api_key", in order
     */
    static SortedSet<String> names(Map<ProviderId, Set<String>> settings) {
        var names = new TreeSet<String>();
        settings.forEach((id, settingNames) -> settingNames.forEach(name -> names.add(entryName(id, name))));
        return names;
    }

    private static String entryName(ProviderId id, String setting) {
        return PREFIX + id.value() + "/" + setting;
    }

    private void writeAndCheck(String name, String value) throws KeychainException {
        keychain.write(name, value);
        if (keychain.read(name).filter(value::equals).isEmpty()) {
            throw new KeychainException(KeychainException.Kind.REFUSED,
                    "the keychain did not keep a value that was written to it");
        }
    }

    /** Whether this failure to read the index counts as a damaged index: a refusal, without a file to fall back to. */
    private boolean damagesTheIndex(KeychainException failure) {
        return fallback == KeyFallback.NONE && failure.kind() == KeychainException.Kind.REFUSED;
    }

    private List<String> index() throws KeychainException {
        Optional<String> text;
        try {
            text = keychain.read(INDEX);
        } catch (KeychainException e) {
            if (damagesTheIndex(e)) {
                throw new DamagedIndex();
            }
            throw e;
        }
        if (text.isEmpty()) {
            return List.of();
        }
        var names = new ArrayList<String>();
        try {
            var stored = StoredJson.parse(text.get());
            if (!stored.isArray()) {
                throw new DamagedIndex();
            }
            for (var name : stored.values()) {
                if (!name.isString() || !isEntryName(name.stringValue())) {
                    throw new DamagedIndex();
                }
                names.add(name.stringValue());
            }
        } catch (JacksonException ignored) {
            throw new DamagedIndex();
        }
        return names;
    }

    private static boolean isEntryName(String name) {
        if (name == null || !name.startsWith(PREFIX)) {
            return false;
        }
        var end = name.indexOf('/', PREFIX.length());
        return end > 0 && ProviderId.isValid(name.substring(PREFIX.length(), end));
    }
}
