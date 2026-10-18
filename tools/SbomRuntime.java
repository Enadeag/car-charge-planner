import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Completes the SBOM that Maven writes (cyclonedx-maven-plugin, the libraries) with the Java runtime that jlink made,
 * so the SBOM of a download names everything in it. JavaFX is in the runtime too, but Maven's SBOM already lists it,
 * as the {@code org.openjfx} jars of the same version as the jmods it is linked from.
 *
 * <pre>
 * java -cp 'target/lib/*' tools/SbomRuntime.java target/car-charge-planner.cdx.json target/runtime \
 *     target/dist/car-charge-planner-1.0.0-linux.cdx.json
 * </pre>
 *
 * <p>Run it with the JDK whose jlink made the runtime: the runtime's {@code release} file only says the Java version
 * and the modules, so the vendor and the full version come from the JDK that runs this, and it exits with code 1 when
 * that JDK has another version than the runtime. It needs Jackson on the class path, which the build copies to
 * target/lib.
 */
public final class SbomRuntime {

    private static final String REF = "java-runtime";

    private SbomRuntime() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println(
                    "usage: java -cp <jackson jars> tools/SbomRuntime.java <maven sbom> <runtime folder> <output>");
            System.exit(2);
        }
        var release = release(Path.of(args[1], "release"));
        var linked = release.getOrDefault("JAVA_VERSION", "");
        var jdk = Runtime.version().version().stream().map(String::valueOf).collect(Collectors.joining("."));
        if (!linked.equals(jdk)) {
            System.err.println(args[1] + " holds Java " + linked + ", but this JDK is " + jdk
                    + ": run this with the JDK whose jlink made the runtime");
            System.exit(1);
        }

        var mapper = JsonMapper.builder().build();
        var sbom = (ObjectNode) mapper.readTree(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        ((ArrayNode) sbom.get("components")).add(runtime(mapper, jdk, release.getOrDefault("MODULES", "")));
        var dependencies = (ArrayNode) sbom.get("dependencies");
        var app = sbom.get("metadata").get("component").get("bom-ref").asString();
        for (JsonNode dependency : dependencies) {
            if (dependency.get("ref").asString().equals(app)) {
                ((ArrayNode) dependency.get("dependsOn")).add(REF);
            }
        }
        dependencies.addObject().put("ref", REF).putArray("dependsOn");

        Files.writeString(Path.of(args[2]), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sbom) + "\n",
                StandardCharsets.UTF_8);
    }

    /** The runtime as a CycloneDX component; NVD files the Java SE security fixes under OpenJDK's CPE. */
    private static ObjectNode runtime(JsonMapper mapper, String version, String modules) {
        var full = Runtime.version().toString();
        var vendor = System.getProperty("java.vendor");
        var build = System.getProperty("java.vendor.version", "");
        var component = mapper.createObjectNode()
                .put("type", "platform")
                .put("bom-ref", REF);
        component.putObject("supplier").put("name", vendor);
        component.put("name", "openjdk")
                .put("version", full)
                .put("description", "The Java runtime in the download, made by jlink from "
                        + (build.isBlank() ? vendor + " " + full : build))
                .put("cpe", "cpe:2.3:a:oracle:openjdk:" + version + ":*:*:*:*:*:*:*")
                .put("purl", "pkg:generic/openjdk@" + URLEncoder.encode(full, StandardCharsets.UTF_8));
        var properties = component.putArray("properties");
        properties.addObject().put("name", "jlink.modules").put("value", modules);
        if (!build.isBlank()) {
            properties.addObject().put("name", "java.vendor.version").put("value", build);
        }
        return component;
    }

    /** The {@code KEY="value"} lines of a runtime's {@code release} file. */
    private static Map<String, String> release(Path file) throws IOException {
        var values = new HashMap<String, String>();
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.map(line -> line.split("=", 2)).filter(pair -> pair.length == 2)
                    .forEach(pair -> values.put(pair[0].strip(), pair[1].strip().replaceAll("^\"|\"$", "")));
        }
        return values;
    }
}
