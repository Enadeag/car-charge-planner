package nl.enadeag.carchargeplanner.buildlogic;

import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/**
 * The Android app's version code, which follows from its version name alone (#272), so a release commit sets only
 * the name: {@code major × 10000 + minor × 100 + patch}, such as 100 for 0.1.0.
 */
public final class AppVersion {

    // no leading zeros, and at most 9 digits a part, so a part always fits a long
    private static final Pattern NAME =
            Pattern.compile("(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})(-SNAPSHOT)?");

    // the highest version code Google Play accepts; Android itself takes up to Integer.MAX_VALUE
    private static final long HIGHEST_CODE = 2_100_000_000L;

    private AppVersion() {
    }

    /**
     * The version code of a version name.
     *
     * @param versionName {@code X.Y.Z}, or {@code X.Y.Z-SNAPSHOT} between releases, whose code is that of
     *     {@code X.Y.Z}; never null
     * @return {@code major × 10000 + minor × 100 + patch}, from 1 up to 2,100,000,000
     * @throws GradleException when the name has another form (a leading zero, a pre-release other than
     *     {@code -SNAPSHOT}, a missing part), when its minor or patch version is above 99, so the code of one version
     *     could be that of another, or when the code would be 0 or above 2,100,000,000
     */
    public static int versionCode(String versionName) {
        var match = NAME.matcher(versionName);
        if (!match.matches()) {
            throw new GradleException("version name '" + versionName + "' is not X.Y.Z or X.Y.Z-SNAPSHOT (digits "
                    + "without leading zeros, such as 0.1.0), from which the version code follows");
        }
        var major = Long.parseLong(match.group(1));
        var minor = Long.parseLong(match.group(2));
        var patch = Long.parseLong(match.group(3));
        if (minor > 99 || patch > 99) {
            throw new GradleException("version name '" + versionName + "' has a minor or patch version above 99, "
                    + "which the version code (major × 10000 + minor × 100 + patch) cannot hold");
        }
        var code = major * 10_000 + minor * 100 + patch;
        if (code == 0 || code > HIGHEST_CODE) {
            throw new GradleException("version name '" + versionName + "' gives version code " + code
                    + ", which must be from 1 up to " + HIGHEST_CODE);
        }
        return (int) code;
    }
}
