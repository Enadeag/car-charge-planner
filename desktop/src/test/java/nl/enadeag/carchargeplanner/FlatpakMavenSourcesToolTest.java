package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs {@code tools/FlatpakMavenSources.java} as {@code tools/flatpak-maven-sources.sh} does, against a repository
 * served on this computer in place of Maven Central, which publishes each file's SHA-1.
 */
class FlatpakMavenSourcesToolTest {

    private static final String JAR = "org/example/lib/1.0/lib-1.0.jar";
    private static final String POM = "org/example/lib/1.0/lib-1.0.pom";

    @TempDir
    Path folder;

    private HttpServer central;
    private final Map<String, String> published = new ConcurrentHashMap<>();

    private record Run(int exitCode, String out, String err) {
    }

    @BeforeEach
    void serveTheChecksums() throws IOException {
        central = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        central.createContext("/maven2/", exchange -> {
            var body = published.get(exchange.getRequestURI().getPath().substring("/maven2/".length()));
            var bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : bytes.length);
            if (body != null) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        central.start();
    }

    @AfterEach
    void stop() {
        central.stop(0);
    }

    private String base() {
        return "http://localhost:" + central.getAddress().getPort() + "/maven2/";
    }

    /** A local repository as a build leaves it: two files Maven reads, and its own bookkeeping. */
    private Path repository() throws IOException {
        var repository = folder.resolve("repository");
        Files.createDirectories(repository.resolve("org/example/lib/1.0"));
        Files.writeString(repository.resolve(JAR), "the jar");
        Files.writeString(repository.resolve(POM), "<project/>");
        Files.writeString(repository.resolve("org/example/lib/1.0/_remote.repositories"), "lib-1.0.jar>central=");
        Files.writeString(repository.resolve("org/example/lib/1.0/lib-1.0.jar.sha1"), "abc");
        Files.writeString(repository.resolve("org/example/lib/1.0/lib-1.0.pom.lastUpdated"), "");
        Files.createDirectories(repository.resolve(".meta"));
        Files.writeString(repository.resolve(".meta/prefixes-central.txt"), "/org");
        return repository;
    }

    private Run sources(Path repository, Path out) throws IOException, InterruptedException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var process = new ProcessBuilder(java, Path.of("tools", "FlatpakMavenSources.java").toString(),
                repository.toString(), out.toString(), base()).start();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Run(process.exitValue(), stdout, stderr);
    }

    private static String digest(String algorithm, String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm)
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void listsEachFileMavenReadsWithItsAddressAndSha256ForTheLocalRepository() throws Exception {
        published.put(JAR + ".sha1", digest("SHA-1", "the jar") + "  lib-1.0.jar\n");
        published.put(POM + ".sha1", digest("SHA-1", "<project/>"));
        var out = folder.resolve("maven-sources.json");

        var run = sources(repository(), out);

        assertThat(run.exitCode()).as(run.err()).isZero();
        assertThat(run.out()).startsWith("2 files, ");
        assertThat(Files.readString(out)).isEqualTo("""
                [
                  {
                    "type": "file",
                    "url": "%1$s%2$s",
                    "sha256": "%3$s",
                    "dest": "maven-local/org/example/lib/1.0",
                    "dest-filename": "lib-1.0.jar"
                  },
                  {
                    "type": "file",
                    "url": "%1$s%4$s",
                    "sha256": "%5$s",
                    "dest": "maven-local/org/example/lib/1.0",
                    "dest-filename": "lib-1.0.pom"
                  }
                ]
                """.formatted(base(), JAR, digest("SHA-256", "the jar"), POM, digest("SHA-256", "<project/>")));
    }

    @Test
    void aFileWhoseSha1IsNotThePublishedOneOrHasNoneFailsAndNothingIsWritten() throws Exception {
        published.put(JAR + ".sha1", digest("SHA-1", "another jar"));
        var out = folder.resolve("maven-sources.json");

        var run = sources(repository(), out);

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.err()).contains(JAR + ": SHA-1 " + digest("SHA-1", "the jar") + " differs from the published",
                POM + ": no published SHA-1 (404");
        assertThat(out).doesNotExist();
    }
}
