package io.github.enadeag.carchargeplanner.engine.secrets;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The secrets as keychain entries: one per value, named {@code forecast/<provider>/<key>}, so other integrations can
 * add their own prefix. A keychain cannot list its entries, so one more entry, {@code index}, holds the names as a
 * JSON list. It is in the keychain, not the config, so that names and values cannot get out of step when the config is
 * copied or deleted; it holds no values.
 */
final class KeychainEntries {

    /** The name of the entry that lists the others. */
    static final String INDEX = "index";
    private static final String PREFIX = "forecast/";

    private final Keychain keychain;
    private final JsonMapper json = new JsonMapper();

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
     * @return false when there is no keychain, or it cannot be read (locked, or no logon session on Windows)
     */
    boolean usable() {
        try {
            keychain.read(INDEX);
            return true;
        } catch (KeychainException e) {
            return false;
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
                    byProvider.computeIfAbsent(new ProviderId(parts[0]), id -> new HashMap<>()).put(parts[1], value));
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
        } catch (KeychainException e) {
            old = List.of(); // a damaged index: its entries are lost to us, not worth stopping for
        }
        for (var entry : wanted.entrySet()) {
            writeAndCheck(entry.getKey(), entry.getValue());
        }
        writeAndCheck(INDEX, json.writeValueAsString(new ArrayList<>(wanted.keySet())));
        for (var name : old) {
            if (!wanted.containsKey(name)) {
                keychain.delete(name);
            }
        }
    }

    private void writeAndCheck(String name, String value) throws KeychainException {
        keychain.write(name, value);
        if (!keychain.read(name).filter(value::equals).isPresent()) {
            throw new KeychainException("the keychain did not keep a value that was written to it");
        }
    }

    private List<String> index() throws KeychainException {
        var text = keychain.read(INDEX);
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> names;
        try {
            names = json.readValue(text.get(), new TypeReference<List<String>>() { });
        } catch (JacksonException e) {
            throw new KeychainException("the keychain's index of names is damaged");
        }
        if (names == null || !names.stream().allMatch(KeychainEntries::isEntryName)) {
            throw new KeychainException("the keychain's index of names is damaged");
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
