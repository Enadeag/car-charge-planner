import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Prints the release notes for a version: its CHANGELOG.md section plus how to install and check it, and which Java
 * runtime the downloads bring.
 *
 * <pre>java tools/ReleaseNotes.java 0.3.0 CHANGELOG.md &gt; notes.md</pre>
 *
 * <p>With {@code --android}, the notes of an Android release (#272): its section is {@code ## [Android X.Y.Z]}, and
 * the installing text is the APK's, with the SHA-256 fingerprint of the release key's certificate.
 *
 * <pre>java tools/ReleaseNotes.java --android 0.1.0 CHANGELOG.md &lt;fingerprint&gt; &gt; notes.md</pre>
 *
 * <p>A single source file that the JDK runs as it is, without Maven: the release workflows need only a JDK. The
 * desktop's notes name the runtime of the JDK that runs it, so the desktop's release workflow runs it on the Java
 * version it packages; the Android notes name no runtime. Exits with code 1 when the changelog has no section for the
 * version, or an empty one, so a release cannot go out without notes, and with code 2 on wrong arguments.
 */
public final class ReleaseNotes {

    private static final String REPOSITORY = "https://github.com/Enadeag/car-charge-planner";

    private static final String INSTALLING = """
            ### Installing

            - **Windows:** download `car-charge-planner-%1$s.exe` and `SHA256SUMS`. Check the download in PowerShell with `(Get-FileHash .\\car-charge-planner-%1$s.exe -Algorithm SHA256).Hash` against the line in `SHA256SUMS`. The installer is not code-signed yet, so Windows shows "Windows protected your PC": choose **More info**, then **Run anyway**.
            - **Debian, Ubuntu:** download `car-charge-planner_%1$s_amd64.deb` and `SHA256SUMS`, check it with `sha256sum --ignore-missing -c SHA256SUMS` and install it with `sudo apt install ./car-charge-planner_%1$s_amd64.deb`. It puts the app in `/opt/car-charge-planner` and in the application menu.
            - **Fedora, openSUSE:** download `car-charge-planner-%1$s-1.x86_64.rpm` and `SHA256SUMS`, check it with `sha256sum --ignore-missing -c SHA256SUMS` and install it with `sudo dnf install ./car-charge-planner-%1$s-1.x86_64.rpm` (openSUSE: `sudo zypper install ./car-charge-planner-%1$s-1.x86_64.rpm`).
            - **Other Linux:** download `car-charge-planner-%1$s-linux.tar.gz` and `SHA256SUMS`, check it with `sha256sum --ignore-missing -c SHA256SUMS`, unpack it and start `bin/car-charge-planner`.

            The .deb and .rpm do not update themselves: install the next release's over this one. [Installing](%2$s/blob/v%1$s/docs/install.md) has updating and uninstalling. Every download brings its own Java runtime: %3$s. `car-charge-planner-%1$s-linux.cdx.json` and `car-charge-planner-%1$s-windows.cdx.json` list what the downloads hold (CycloneDX SBOM; the Linux one covers the tar.gz, the .deb and the .rpm, which hold the same app), and GitHub attests that every file was built here from tag `v%1$s`: check one with `gh attestation verify <file> -R Enadeag/car-charge-planner`.

            Full details: [CHANGELOG.md](%2$s/blob/v%1$s/CHANGELOG.md).
            """;

    private static final String INSTALLING_ANDROID = """
            ### Installing

            The app needs Android 10 or newer. Download `car-charge-planner-android-%1$s.apk` and `SHA256SUMS`, and check the APK with `sha256sum --ignore-missing -c SHA256SUMS`. Open the APK on the phone and allow installing from your browser or Files when Android asks; Play Protect may warn about an app it does not know. An update is installed the same way, over this one.

            The APK is signed with the app's release key, whose certificate has the SHA-256 fingerprint `%3$s`. Check it with `apksigner verify --print-certs car-charge-planner-android-%1$s.apk` (from the Android SDK's build-tools): the line `Signer #1 certificate SHA-256 digest` must show that fingerprint. Android installs an update only when it is signed with the same key. GitHub attests that the APK was built here from tag `android-v%1$s`: check it with `gh attestation verify car-charge-planner-android-%1$s.apk -R Enadeag/car-charge-planner`.

            Full details: [CHANGELOG.md](%2$s/blob/android-v%1$s/CHANGELOG.md).
            """;

    private ReleaseNotes() {
    }

    public static void main(String[] args) throws IOException {
        var android = args.length > 0 && args[0].equals("--android");
        if (android ? (args.length != 4 || args[3].isBlank()) : args.length != 2) {
            System.err.println("usage: java tools/ReleaseNotes.java <version> <path/to/CHANGELOG.md>");
            System.err.println("       java tools/ReleaseNotes.java --android <version> <path/to/CHANGELOG.md> "
                    + "<certificate SHA-256 fingerprint>");
            System.exit(2);
        }
        var version = android ? args[1] : args[0];
        var file = android ? args[2] : args[1];
        var heading = android ? "Android " + version : version;
        var changelog = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        var body = section(changelog, heading);
        if (body.isEmpty()) {
            System.err.println(file + " has no section for " + heading + ": add one before releasing");
            System.exit(1);
        }
        var installing = android
                ? INSTALLING_ANDROID.formatted(version, REPOSITORY, args[3])
                : INSTALLING.formatted(version, REPOSITORY,
                        "Java " + Runtime.version() + " (" + System.getProperty("java.vendor") + ")");
        var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.print(body.get() + "\n\n" + installing);
    }

    /** The text under {@code ## [heading]}, up to the next {@code ## [} heading or link definition, if not blank. */
    private static Optional<String> section(String changelog, String heading) {
        var pattern = Pattern.compile(
                "^## \\[" + Pattern.quote(heading) + "\\][^\\n]*\\n(.*?)(?=^## \\[|^\\[[^\\]]+\\]: |\\z)",
                Pattern.MULTILINE | Pattern.DOTALL);
        var match = pattern.matcher(changelog);
        if (!match.find() || match.group(1).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(match.group(1).strip());
    }
}
