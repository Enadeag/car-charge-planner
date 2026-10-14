package spike;

import com.github.javakeyring.Keyring;

/**
 * Question 6: store, read back and delete one secret in the OS keychain.
 * Windows (and macOS): java-keyring (Credential Manager through JNA).
 * Linux: java-keyring's Secret Service backend needs secret-service 1.8 + dbus-java 4, which clashes with
 * the dbus-java 5 used for the tray: with this project's class path it reports "No available keyring
 * backend found". Use {@link SecretDbus} on Linux instead.
 */
public class Secret {
    static final String SERVICE = "car-charge-planner-spike";
    static final String ACCOUNT = "example-api-token";

    public static void main(String[] args) throws Exception {
        var value = "example-secret-" + System.nanoTime();
        boolean keep = java.util.List.of(args).contains("--keep");
        javaKeyring(value, keep);
    }

    static void javaKeyring(String value, boolean keep) throws Exception {
        try (var keyring = Keyring.create()) {
            System.out.println("java-keyring backend: " + keyring.getKeyringStorageType());
            keyring.setPassword(SERVICE, ACCOUNT, value);
            var back = keyring.getPassword(SERVICE, ACCOUNT);
            System.out.println("stored and read back: " + (value.equals(back) ? "OK, identical" : "MISMATCH: " + back));
            if (!keep) {
                keyring.deletePassword(SERVICE, ACCOUNT);
                System.out.println("deleted again");
            }
        }
    }
}
