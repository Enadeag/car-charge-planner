import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Prints the release notes for a version: its CHANGELOG.md section plus how to install it.
 *
 * <pre>java tools/ReleaseNotes.java 0.3.0 CHANGELOG.md &gt; notes.md</pre>
 *
 * <p>A single source file that the JDK runs as it is, without Maven: the release workflow needs only a JDK. Exits with
 * code 1 when the changelog has no section for the version, or an empty one, so a release cannot go out without notes.
 */
public final class ReleaseNotes {

    private static final String REPOSITORY = "https://github.com/Enadeag/car-charge-planner";

    private static final String INSTALLING = """
            ### Installing

            - **Windows:** download `car-charge-planner-%1$s.exe` and `SHA256SUMS`. Check the download in PowerShell with `(Get-FileHash .\\car-charge-planner-%1$s.exe -Algorithm SHA256).Hash` against the line in `SHA256SUMS`. The installer is not code-signed yet, so Windows shows "Windows protected your PC": choose **More info**, then **Run anyway**.
            - **Linux:** download `car-charge-planner-%1$s-linux.tar.gz` and `SHA256SUMS`, check it with `sha256sum --ignore-missing -c SHA256SUMS`, unpack it and start `bin/car-charge-planner`; see the README.

            Full details: [CHANGELOG.md](%2$s/blob/v%1$s/CHANGELOG.md).
            """;

    private ReleaseNotes() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: java tools/ReleaseNotes.java <version> <path/to/CHANGELOG.md>");
            System.exit(2);
        }
        var version = args[0];
        var changelog = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        var body = section(changelog, version);
        if (body.isEmpty()) {
            System.err.println(args[1] + " has no section for " + version + ": add one before releasing");
            System.exit(1);
        }
        var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.print(body.get() + "\n\n" + INSTALLING.formatted(version, REPOSITORY));
    }

    /** The text under {@code ## [version]}, up to the next {@code ## [} heading or link definition, if not blank. */
    private static Optional<String> section(String changelog, String version) {
        var heading = Pattern.compile("^## \\[" + Pattern.quote(version) + "\\][^\\n]*\\n(.*?)(?=^## \\[|^\\[[^\\]]+\\]: |\\z)",
                Pattern.MULTILINE | Pattern.DOTALL);
        var match = heading.matcher(changelog);
        if (!match.find() || match.group(1).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(match.group(1).strip());
    }
}
