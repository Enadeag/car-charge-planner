package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;

/**
 * The secrets as keychain entries: one per value, named {@code forecast/<provider>/<key>}, so other integrations can
 * add their own prefix. A keychain cannot list its entries, so one more entry, {@code index}, holds the names as a
 * JSON list. It is in the keychain, not the config, so that names and values cannot get out of step when the config is
 * copied or deleted; it holds no values.
 */
final class KeychainEntries {

    /** Whether the keychain can be used now. */
    enum State {
        /** It answered: the entries can be read and written. */
        USABLE,
        /** It is there but locked: nothing can be read or written until the owner unlocks it. */
        LOCKED,
        /** There is none, it does not answer, or it refused. */
        UNUSABLE
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
     * @return usable; locked; or unusable when there is no keychain, it does not answer, or it refused (such as
     *     without a logon session on Windows)
     */
    State state() {
        try {
            keychain.read(INDEX);
            return State.USABLE;
        } catch (KeychainException e) {
            return e.kind() == KeychainException.Kind.LOCKED ? State.LOCKED : State.UNUSABLE;
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
                    byProvider.computeIfAbsent(new ProviderId(parts[0]), _ -> new HashMap<>()).put(parts[1], value));
        }
        return new Secrets(byProvider);
    }

    /**
     * Replaces everything with {@code secrets}. Each value is read back; the index is written last, and entries it no
     * longer lists are removed.
     *
     * @param secrets the secrets to keep, with names that can be split up again
     * @throws KeychainException when the keychain fails or does not keep a value
     */
    void write(Secrets secrets) throws KeychainException {
        var wanted = new TreeMap<String, String>();
        secrets.byProvider().forEach((id, values) ->
                values.forEach((key, value) -> wanted.put(PREFIX + id.value() + "/" + key, value)));
        List<String> old;
        try {
            old = index();
        } catch (KeychainException _) {
            old = List.of(); // a damaged index: its entries are lost to us, not worth stopping for
        }
        for (var entry : wanted.entrySet()) {
            writeAndCheck(entry.getKey(), entry.getValue());
        }
        var index = StoredJson.array();
        wanted.keySet().forEach(index::add);
        writeAndCheck(INDEX, StoredJson.compact(index));
        for (var name : old) {
            if (!wanted.containsKey(name)) {
                keychain.delete(name);
            }
        }
    }

    private void writeAndCheck(String name, String value) throws KeychainException {
        keychain.write(name, value);
        if (!keychain.read(name).filter(value::equals).isPresent()) {
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
        } catch (JacksonException _) {
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
