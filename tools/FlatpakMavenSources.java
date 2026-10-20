import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Writes the Maven sources of the Flatpak's manifest: every file of a local Maven repository that a build filled, as a
 * flatpak-builder source with its address on Maven Central and its SHA-256, which flatpak-builder puts back into
 * {@code maven-local/} so that Maven can build offline.
 *
 * <pre>java tools/FlatpakMavenSources.java &lt;local repository&gt; &lt;sources.json&gt; [&lt;repository address&gt;]</pre>
 *
 * <p>Run it through {@code tools/flatpak-maven-sources.sh}, which fills an empty repository with the Flatpak's own
 * Maven build first. The SHA-256 is computed from the downloaded file, which is only trusted when its SHA-1 is the one
 * that Maven Central publishes beside it: the tool fetches each {@code .sha1} and exits with code 1 on a file whose
 * SHA-1 differs or has none there. Maven's own bookkeeping files are left out.
 */
public final class FlatpakMavenSources {

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String DEST = "maven-local";
    private static final int PARALLEL = 8;

    private FlatpakMavenSources() {
    }

    /** One file of the repository, with its checksums. */
    private record Artifact(String path, String sha256, String sha1) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("usage: java tools/FlatpakMavenSources.java <local repository> <sources.json>"
                    + " [<repository address>]");
            System.exit(2);
        }
        var repository = Path.of(args[0]);
        var base = args.length == 3 ? args[2] : CENTRAL;
        var artifacts = artifacts(repository);
        var problems = checkAgainst(base, artifacts);
        if (!problems.isEmpty()) {
            problems.forEach(System.err::println);
            System.exit(1);
        }
        Files.writeString(Path.of(args[1]), json(base, artifacts), StandardCharsets.UTF_8);
        System.out.println(artifacts.size() + " files, " + Files.size(Path.of(args[1])) + " bytes of sources");
    }

    /** The repository's files that Maven reads, by path, with their checksums. */
    private static List<Artifact> artifacts(Path repository) throws IOException {
        try (var files = Files.walk(repository)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.startsWith(repository.resolve(".meta")))
                    .filter(file -> !bookkeeping(file.getFileName().toString()))
                    .map(file -> new Artifact(repository.relativize(file).toString().replace('\\', '/'),
                            digest("SHA-256", file), digest("SHA-1", file)))
                    .sorted((a, b) -> a.path().compareTo(b.path()))
                    .toList();
        }
    }

    /**
     * Maven's notes on where a file came from and when it last looked, and checksums: none is needed offline, nor
     * the lists of what each remote repository holds in {@code .meta/}.
     */
    private static boolean bookkeeping(String name) {
        return name.equals("_remote.repositories") || name.equals("resolver-status.properties")
                || name.endsWith(".lastUpdated") || name.startsWith("maven-metadata-")
                || name.endsWith(".sha1") || name.endsWith(".sha256") || name.endsWith(".sha512")
                || name.endsWith(".md5") || name.endsWith(".asc");
    }

    /** Fetches each published SHA-1, a few at a time; what is wrong, one line per file. */
    private static List<String> checkAgainst(String base, List<Artifact> artifacts)
            throws InterruptedException, ExecutionException {
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        var slots = new Semaphore(PARALLEL);
        var checks = new ArrayList<Future<String>>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var artifact : artifacts) {
                checks.add(threads.submit(() -> {
                    slots.acquire();
                    try {
                        return check(http, base, artifact);
                    } finally {
                        slots.release();
                    }
                }));
            }
        }
        var problems = new ArrayList<String>();
        for (var check : checks) {
            var problem = check.get();
            if (!problem.isEmpty()) {
                problems.add(problem);
            }
        }
        return problems;
    }

    private static String check(HttpClient http, String base, Artifact artifact) throws InterruptedException {
        var address = base + artifact.path() + ".sha1";
        try {
            var reply = http.send(HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (reply.statusCode() != 200) {
                return artifact.path() + ": no published SHA-1 (" + reply.statusCode() + " for " + address + ")";
            }
            // some .sha1 files hold the file name after the checksum
            var published = reply.body().strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
            return published.equals(artifact.sha1()) ? ""
                    : artifact.path() + ": SHA-1 " + artifact.sha1() + " differs from the published " + published;
        } catch (IOException e) {
            return artifact.path() + ": cannot fetch " + address + " (" + e + ")";
        }
    }

    private static String json(String base, List<Artifact> artifacts) {
        return artifacts.stream().map(artifact -> {
            var slash = artifact.path().lastIndexOf('/');
            var folder = slash < 0 ? "" : "/" + artifact.path().substring(0, slash);
            return String.format(Locale.ROOT, """
                      {
                        "type": "file",
                        "url": "%s",
                        "sha256": "%s",
                        "dest": "%s",
                        "dest-filename": "%s"
                      }""", base + artifact.path(), artifact.sha256(), DEST + folder,
                    artifact.path().substring(slash + 1)).indent(2).stripTrailing();
        }).collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    private static String digest(String algorithm, Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
