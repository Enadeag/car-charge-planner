package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

/**
 * Gives the Android build the versions of the libraries it shares with the Maven build, as the parent pom has them,
 * so they are kept in one place (ADR 0010): a settings script hands it the {@code libs} catalog it creates. The poms
 * are read as inputs of the configuration, so a changed pom does not leave an old version in the configuration cache.
 * A version the Maven build pins anywhere else, which Android would not read, fails the configuration.
 */
public abstract class MavenVersions {

    /**
     * Per version of the catalog, the library of the parent pom's {@code dependencyManagement} whose version it is: the
     * BOM for Jackson and JUnit, which manages the versions of the rest of each.
     */
    private static final Map<String, Declared> FROM_PARENT_POM = new TreeMap<>(Map.of(
            "jackson", new Declared("tools.jackson", "jackson-bom"),
            "slf4j", new Declared("org.slf4j", "slf4j-api"),
            "junit", new Declared("org.junit", "junit-bom"),
            "assertj", new Declared("org.assertj", "assertj-core"),
            "archunit", new Declared("com.tngtech.archunit", "archunit")));

    /** Makes the extension; Gradle calls it. */
    public MavenVersions() {
    }

    /**
     * The names of the versions {@link #addTo} puts in a catalog.
     *
     * @return the names, such as {@code jackson}
     */
    static Set<String> versionNames() {
        return FROM_PARENT_POM.keySet();
    }

    /**
     * Where Gradle's inputs of the configuration come from.
     *
     * @return Gradle's factory
     */
    @Inject
    protected abstract ProviderFactory getProviders();

    /**
     * Turns a file into one of Gradle's.
     *
     * @return Gradle's factory
     */
    @Inject
    protected abstract ObjectFactory getObjects();

    /**
     * Adds the versions of {@code FROM_PARENT_POM} to a catalog, as the parent pom gives them, and checks that the
     * Maven build pins no other version of those libraries' groups, which Android would not read: no other entry of
     * the parent's {@code dependencyManagement} in those groups (a BOM import apart) unless it takes its version from
     * the same property as the entry of its group, and no dependency of {@code engine/pom.xml}, which sits beside the
     * parent pom, with a {@code <version>} of its own. No versions are compared.
     *
     * @param catalog the catalog the settings script creates; its libraries name these versions with
     *     {@code version.ref}
     * @param parentPom the Maven build's parent {@code pom.xml}
     * @throws GradleException when a pom is missing or unreadable, the parent lacks one of the libraries or a property
     *     its version names, a version is not a plain one, or a version is pinned where Android does not read it; the
     *     message names the library and the pom. There is no fallback version.
     */
    public void addTo(VersionCatalogBuilder catalog, File parentPom) {
        var enginePom = parentPom.toPath().resolveSibling("engine/pom.xml").toFile();
        var parent = contents(parentPom, "takes the versions of " + String.join(", ", FROM_PARENT_POM.keySet())
                + " from");
        var engine = contents(enginePom, "checks that no version is pinned in");
        try {
            checkNothingElseIsPinned(parent, parentPom, engine, enginePom);
        } catch (IOException | IllegalArgumentException e) {
            throw new GradleException("The Android build cannot check the versions the Maven build pins: "
                    + e.getMessage(), e);
        }
        for (var version : FROM_PARENT_POM.entrySet()) {
            try {
                catalog.version(version.getKey(),
                        MavenPoms.managedVersion(parent, parentPom.toString(), version.getValue()));
            } catch (IOException | IllegalArgumentException e) {
                throw new GradleException("The Android build takes the catalog's version \"" + version.getKey()
                        + "\" from the Maven build, but " + e.getMessage(), e);
            }
        }
    }

    private byte[] contents(File pom, String what) {
        var contents = getProviders().fileContents(getObjects().fileProperty().fileValue(pom)).getAsBytes()
                .getOrNull();
        if (contents == null) {
            throw new GradleException("The Android build " + what + " the Maven build's " + pom
                    + ", which is not there");
        }
        return contents;
    }

    private static void checkNothingElseIsPinned(byte[] parent, File parentPom, byte[] engine, File enginePom)
            throws IOException {
        List<Declared> read = List.copyOf(FROM_PARENT_POM.values());
        var managed = MavenPoms.managedLibraries(parent, parentPom.toString());
        for (var entry : managed) {
            if (entry.imported() || read.contains(entry.library())) {
                continue;
            }
            for (var own : managed) {
                boolean sameGroup = read.contains(own.library())
                        && Groups.isIn(entry.library().group(), own.library().group());
                if (sameGroup && !Objects.equals(entry.version(), own.version())) {
                    throw new GradleException("Android does not read the version of " + entry.library().module()
                            + " that " + parentPom + " manages: the Android build takes only the versions of "
                            + read.stream().map(Declared::module).collect(Collectors.joining(", "))
                            + " from it. Let it take its version from "
                            + own.version() + ", as " + own.library().module() + " does, or have MavenVersions"
                            + " read it.");
                }
            }
        }
        var pinned = MavenPoms.librariesWithOwnVersion(engine, enginePom.toString());
        if (!pinned.isEmpty()) {
            throw new GradleException("Android does not read the version that " + enginePom + " gives "
                    + pinned.get(0).module() + ": a version belongs in the dependencyManagement of " + parentPom
                    + ", and the Android build reads only some of those.");
        }
    }
}
