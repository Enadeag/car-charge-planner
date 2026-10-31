package nl.enadeag.carchargeplanner.buildlogic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Google's own libraries, which the app must not use (ADR 0010): F-Droid builds only apps without them, and Google
 * Play stays possible without them too. Google's open-source build tools and AndroidX are other groups.
 */
public final class GoogleLibraries {

    /** The groups refused, each with its subgroups: Play Services, Firebase and the Play libraries. */
    public static final Set<String> GROUPS = Set.of("com.google.android.gms", "com.google.firebase",
            "com.google.android.play");

    private GoogleLibraries() {
    }

    /**
     * Finds the refused libraries in what the configurations resolved to.
     *
     * @param modulesByConfiguration per configuration's name, the {@code group:artifact:version} of each module it
     *     resolved to; never null
     * @return one line per refused module and the configurations it is in, in name order; empty when there is none
     */
    public static List<String> findIn(Map<String, ? extends Collection<String>> modulesByConfiguration) {
        var found = new TreeMap<String, List<String>>();
        modulesByConfiguration.forEach((configuration, modules) -> {
            for (var module : modules) {
                var group = module.substring(0, Math.max(0, module.indexOf(':')));
                if (GROUPS.stream().anyMatch(refused -> MavenPoms.inGroup(group, refused))) {
                    found.computeIfAbsent(module, ignored -> new ArrayList<>()).add(configuration);
                }
            }
        });
        var lines = new ArrayList<String>();
        found.forEach((module, configurations) ->
                lines.add(module + " in " + String.join(", ", configurations.stream().sorted().toList())));
        return lines;
    }
}
