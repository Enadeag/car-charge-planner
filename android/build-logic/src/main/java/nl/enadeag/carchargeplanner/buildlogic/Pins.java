package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which version each declared library is pinned to, and where two builds differ. A library without a version of its own
 * is pinned by the BOM whose group is its group or a parent of it, such as {@code tools.jackson:jackson-bom} for
 * {@code tools.jackson.core:jackson-databind}: the same rule in Maven and in Gradle, so each build's pin reads alike.
 */
public final class Pins {

    private Pins() {
    }

    /**
     * The pin of each library that is not a BOM.
     *
     * @param declared what one build declares, BOMs included; never null
     * @return per {@code group:artifact}, its version, {@code "BOM group:artifact:version"} when a BOM manages it, or
     *     {@code "no version"} when nothing does, in name order
     */
    public static Map<String, String> of(Collection<Declared> declared) {
        var boms = declared.stream().filter(Declared::bom).toList();
        var pins = new TreeMap<String, String>();
        for (var library : declared) {
            if (library.bom()) {
                continue;
            }
            var pin = library.version() != null ? library.version()
                    : bomOf(library, boms).map(bom -> "BOM " + bom.module() + ":" + bom.version()).orElse("no version");
            pins.put(library.module(), pin);
        }
        return pins;
    }

    private static Optional<Declared> bomOf(Declared library, List<Declared> boms) {
        return boms.stream()
                .filter(bom -> library.group().equals(bom.group()) || library.group().startsWith(bom.group() + "."))
                .max(Comparator.comparingInt(bom -> bom.group().length()));
    }

    /**
     * Where the two builds' pins differ: a library only one of them declares, or one pinned to another version.
     *
     * @param maven the Maven build's pins, as {@link #of} gives them
     * @param gradle the Gradle build's pins, as {@link #of} gives them
     * @return one line per difference, in name order; empty when they agree
     */
    public static List<String> differences(Map<String, String> maven, Map<String, String> gradle) {
        var modules = new TreeSet<String>();
        modules.addAll(maven.keySet());
        modules.addAll(gradle.keySet());
        var differences = new ArrayList<String>();
        for (var module : modules) {
            var inMaven = maven.get(module);
            var inGradle = gradle.get(module);
            if (inGradle == null) {
                differences.add(module + ": only in Maven (" + inMaven + ")");
            } else if (inMaven == null) {
                differences.add(module + ": only in Gradle (" + inGradle + ")");
            } else if (!inMaven.equals(inGradle)) {
                differences.add(module + ": Maven " + inMaven + ", Gradle " + inGradle);
            }
        }
        return differences;
    }
}
