package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

/**
 * Gives the Android build the versions of the libraries it shares with the Maven build, as the parent pom has them,
 * so they are kept in one place (ADR 0010): a settings script hands it the {@code libs} catalog it creates. The pom is
 * read as an input of the configuration, so a changed pom does not leave an old version in the configuration cache.
 */
public abstract class MavenVersions {

    /**
     * Per version of the catalog, the library of the parent pom's {@code dependencyManagement} whose version it is: the
     * BOM for Jackson and JUnit, which manages the versions of the rest of each.
     */
    static final Map<String, Declared> FROM_PARENT_POM = new TreeMap<>(Map.of(
            "jackson", new Declared("tools.jackson", "jackson-bom"),
            "slf4j", new Declared("org.slf4j", "slf4j-api"),
            "junit", new Declared("org.junit", "junit-bom"),
            "assertj", new Declared("org.assertj", "assertj-core"),
            "archunit", new Declared("com.tngtech.archunit", "archunit")));

    /** Makes the extension; Gradle calls it. */
    public MavenVersions() {
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
     * Adds the versions {@code jackson}, {@code slf4j}, {@code junit}, {@code assertj} and {@code archunit} to a
     * catalog, as the parent pom gives them.
     *
     * @param catalog the catalog the settings script creates; its libraries name these versions with
     *     {@code version.ref}
     * @param parentPom the Maven build's parent {@code pom.xml}
     * @throws GradleException when the pom is missing or unreadable, or lacks one of the libraries or a property its
     *     version names; the message names it and the pom. There is no fallback version.
     */
    public void addTo(VersionCatalogBuilder catalog, File parentPom) {
        var contents = getProviders().fileContents(getObjects().fileProperty().fileValue(parentPom)).getAsBytes()
                .getOrNull();
        if (contents == null) {
            throw new GradleException("The Android build takes the versions of " + FROM_PARENT_POM.keySet()
                    + " from the Maven build's " + parentPom + ", which is not there");
        }
        for (var version : FROM_PARENT_POM.entrySet()) {
            try {
                catalog.version(version.getKey(),
                        MavenPoms.managedVersion(contents, parentPom.toString(), version.getValue()));
            } catch (IOException | IllegalArgumentException e) {
                throw new GradleException("The Android build takes the catalog's version \"" + version.getKey()
                        + "\" from the Maven build, but " + e.getMessage(), e);
            }
        }
    }
}
