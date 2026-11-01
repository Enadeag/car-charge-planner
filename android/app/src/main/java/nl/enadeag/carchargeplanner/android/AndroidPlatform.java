package nl.enadeag.carchargeplanner.android;

import android.content.Context;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import nl.enadeag.carchargeplanner.android.secrets.AndroidKeystoreKey;
import nl.enadeag.carchargeplanner.android.secrets.EncryptedKeychain;
import nl.enadeag.carchargeplanner.android.secrets.KeychainKey;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;

/**
 * The Android side of the engine's platform seams, made once for the app: the {@link HttpGet}, the files, the keychain
 * and the API keys. The engine's files (settings, trips, the forecast cache) go in the app's files folder, which is
 * private already ({@link FilePrivacy#NONE}) and moves to the owner's next phone; the keychain's file goes in the folder
 * that is never backed up, as its key never leaves this phone. The API keys are kept only in the keychain
 * ({@link KeyFallback#NONE}), never unencrypted.
 */
public final class AndroidPlatform {

    private static final String SECRETS_FILE = "secrets.json";

    private final HttpGet http = new AndroidHttpGet();
    private final PrivateFiles files = new PrivateFiles(FilePrivacy.NONE);
    private final Path dataFolder;
    private final Keychain keychain;
    private final ApiKeys apiKeys;

    /**
     * Makes the parts for the app's own folders.
     *
     * @param context the app's context, whose files folder and never-backed-up folder are used
     * @return the parts; nothing is read, written or made yet
     */
    public static AndroidPlatform of(Context context) {
        return new AndroidPlatform(context.getFilesDir(), context.getNoBackupFilesDir());
    }

    /**
     * Makes the parts for these folders, with the keychain's key in the Android Keystore; nothing is read, written or
     * made yet.
     *
     * @param filesFolder the app's files folder ({@code Context.getFilesDir()}), for the engine's files
     * @param noBackupFolder the app's folder that is never backed up ({@code Context.getNoBackupFilesDir()}), for the
     *     keychain's file
     */
    public AndroidPlatform(File filesFolder, File noBackupFolder) {
        this(filesFolder, noBackupFolder, new AndroidKeystoreKey(AndroidKeystoreKey.ALIAS));
    }

    /**
     * Makes the parts for these folders and this key; nothing is read, written or made yet.
     *
     * @param filesFolder the folder for the engine's files
     * @param noBackupFolder the folder for the keychain's file
     * @param key the key the keychain encrypts with
     */
    AndroidPlatform(File filesFolder, File noBackupFolder, KeychainKey key) {
        dataFolder = filesFolder.toPath();
        var keysFolder = noBackupFolder.toPath();
        keychain = new EncryptedKeychain(keysFolder.resolve(EncryptedKeychain.FILE_NAME), files, key);
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
     * The store of the API keys, encrypted with the keychain's key.
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
