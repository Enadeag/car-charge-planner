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
 * The Android side of the engine's platform seams: the {@link HttpGet}, the files, the keychain and the API keys. The
 * app's {@code Application} makes one set ({@link #of(Context, Clock)}) and keeps it, as {@link ApiKeys} remembers what
 * it read and each {@code HttpGet} keeps a pool of threads. The engine's files (settings, trips, the forecast cache) go
 * in the app's files folder, which is private already ({@link FilePrivacy#NONE}) and moves to the owner's next phone;
 * the keychain's file goes in the folder that is never backed up, as its keys never leave this phone. The API keys are
 * kept only in the keychain ({@link KeyFallback#NONE}), never unencrypted. The keychain and the API keys may block for
 * about a second, so they are used off the main thread.
 */
public final class AndroidPlatform {

    private static final String SECRETS_FILE = "secrets.json";

    private final HttpGet http = new AndroidHttpGet();
    private final PrivateFiles files = new PrivateFiles(FilePrivacy.NONE);
    private final Path dataFolder;
    private final Path keychainFile;
    private final Keychain keychain;
    private final ApiKeys apiKeys;

    /**
     * Makes the parts for the app's own folders; nothing is read, written or made yet. The app's {@code Application}
     * calls this once and hands the parts on.
     *
     * @param context any context of the app; its application's files folder and never-backed-up folder are used
     * @param clock the app's one clock, which tells the API keys when a keychain that failed may be asked again
     * @return a new set of parts
     */
    public static AndroidPlatform of(Context context, Clock clock) {
        var app = context.getApplicationContext();
        return new AndroidPlatform(app.getFilesDir(), app.getNoBackupFilesDir(), new AndroidKeystoreKeys(), clock);
    }

    /**
     * Nothing is read, written or made yet. The app makes its set through {@link #of(Context, Clock)}; tests make
     * their own.
     *
     * @param filesFolder the folder for the engine's files ({@code Context.getFilesDir()})
     * @param noBackupFolder the folder for the keychain's file ({@code Context.getNoBackupFilesDir()})
     * @param clock the app's one clock, which tells the API keys when a keychain that failed may be asked again
     */
    AndroidPlatform(File filesFolder, File noBackupFolder, KeychainKeys keys, Clock clock) {
        dataFolder = filesFolder.toPath();
        var keysFolder = noBackupFolder.toPath();
        keychainFile = keysFolder.resolve(EncryptedKeychain.FILE_NAME);
        keychain = new EncryptedKeychain(keychainFile, files, keys, AndroidKeystoreKeys.ALIAS);
        // without the fallback the file is never read, written, deleted or moved; ApiKeys still takes one
        apiKeys = new ApiKeys(keychain, new SecretsFile(keysFolder.resolve(SECRETS_FILE), files), KeyFallback.NONE,
                clock);
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
