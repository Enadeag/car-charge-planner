package io.github.enadeag.carchargeplanner.engine.secrets;

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

    static final String INDEX = "index";
    private static final String PREFIX = "forecast/";

    private final Keychain keychain;
    private final JsonMapper json = new JsonMapper();

    KeychainEntries(Keychain keychain) {
        this.keychain = keychain;
    }

    /** False when there is no keychain, or it cannot be read (locked, or no logon session on Windows). */
    boolean usable() {
        try {
            keychain.read(INDEX);
            return true;
        } catch (KeychainException e) {
            return false;
        }
    }

    Secrets read() throws KeychainException {
        var byProvider = new HashMap<String, Map<String, String>>();
        for (var name : index()) {
            var parts = name.substring(PREFIX.length()).split("/", 2);
            keychain.read(name).ifPresent(value ->
                    byProvider.computeIfAbsent(parts[0], id -> new HashMap<>()).put(parts[1], value));
        }
        return new Secrets(byProvider);
    }

    /** Replaces everything with {@code secrets}. Each value is read back; the index is written last. */
    void write(Secrets secrets) throws KeychainException {
        var wanted = new TreeMap<String, String>();
        secrets.byProvider().forEach((id, values) -> values.forEach((key, value) -> wanted.put(PREFIX + id + "/" + key, value)));
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
        return name != null && name.startsWith(PREFIX) && name.indexOf('/', PREFIX.length()) > PREFIX.length();
    }
}
