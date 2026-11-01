package nl.enadeag.carchargeplanner.android;

import android.content.Context;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import nl.enadeag.carchargeplanner.android.secrets.AndroidKeystoreKeys;
import nl.enadeag.carchargeplanner.android.secrets.EncryptedKeychain;
import nl.enadeag.carchargeplanner.android.secrets.KeychainKeys;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;

/**
 * The Android side of the engine's platform seams, made once per process ({@link #of(Context)}): the {@link HttpGet},
 * the files, the keychain and the API keys. One set per process, as {@link ApiKeys} remembers what it read and each
 * {@code HttpGet} keeps a pool of threads. The engine's files (settings, trips, the forecast cache) go in the app's
 * files folder, which is private already ({@link FilePrivacy#NONE}) and moves to the owner's next phone; the keychain's
 * file goes in the folder that is never backed up, as its keys never leave this phone. The API keys are kept only in
 * the keychain ({@link KeyFallback#NONE}), never unencrypted.
 */
public final class AndroidPlatform {

    private static final String SECRETS_FILE = "secrets.json";

    private static AndroidPlatform instance;

    private final HttpGet http = new AndroidHttpGet();
    private final PrivateFiles files = new PrivateFiles(FilePrivacy.NONE);
    private final Path dataFolder;
    private final Path keychainFile;
    private final Keychain keychain;
    private final ApiKeys apiKeys;

    /**
     * The parts of this process, made at the first call for the app's own folders; nothing is read, written or made
     * yet.
     *
     * @param context any context of the app; its application's files folder and never-backed-up folder are used
     * @return the same parts for every call
     */
    public static synchronized AndroidPlatform of(Context context) {
        if (instance == null) {
            var app = context.getApplicationContext();
            instance = new AndroidPlatform(app.getFilesDir(), app.getNoBackupFilesDir(), new AndroidKeystoreKeys());
        }
        return instance;
    }

    /**
     * Makes the parts for these folders and these keys; nothing is read, written or made yet. The app has one set,
     * from {@link #of(Context)}; tests make their own.
     *
     * @param filesFolder the folder for the engine's files ({@code Context.getFilesDir()})
     * @param noBackupFolder the folder for the keychain's file ({@code Context.getNoBackupFilesDir()})
     * @param keys the keys the keychain encrypts with
     */
    AndroidPlatform(File filesFolder, File noBackupFolder, KeychainKeys keys) {
        dataFolder = filesFolder.toPath();
        var keysFolder = noBackupFolder.toPath();
        keychainFile = keysFolder.resolve(EncryptedKeychain.FILE_NAME);
        keychain = new EncryptedKeychain(keychainFile, files, keys, AndroidKeystoreKeys.ALIAS);
        // without the fallback the file is never read, written, deleted or moved; ApiKeys still takes one
        apiKeys = new ApiKeys(keychain, new SecretsFile(keysFolder.resolve(SECRETS_FILE), files), KeyFallback.NONE,
                Clock.systemUTC());
    }

    /**
     * The app's way to the network, for the forecast providers.
     *
     * @return the one {@link AndroidHttpGet}
     */
    public HttpGet http() {
        return http;
    }

    /**
     * How the engine writes its files: atomically, with no privacy of its own, as the app's storage is private.
     *
     * @return the writer
     */
    public PrivateFiles files() {
        return files;
    }

    /**
     * Where the engine keeps its files, such as {@code engine.json}, {@code trips.json} and the forecast cache.
     *
     * @return the app's files folder
     */
    public Path dataFolder() {
        return dataFolder;
    }

    /**
     * Where the keychain keeps its entries, which the backup rules keep on the phone.
     *
     * @return {@code keychain.json} in the folder that is never backed up
     */
    public Path keychainFile() {
        return keychainFile;
    }

    /**
     * The store of the API keys, encrypted with the keychain's keys.
     *
     * @return the keychain
     */
    public Keychain keychain() {
        return keychain;
    }

    /**
     * The API keys, kept in {@link #keychain()} only.
     *
     * @return the keys' store, without the fallback to a file
     */
    public ApiKeys apiKeys() {
        return apiKeys;
    }
}
