package nl.enadeag.carchargeplanner.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
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

    private static final Declared JACKSON_BOM = new Declared("tools.jackson", "jackson-bom");
    private static final Declared JUNIT_BOM = new Declared("org.junit", "junit-bom");

    /**
     * The only BOMs the parent pom may import, at its top level: Maven lets the first import that manages a library
     * win, so another BOM could give the engine a version that Android does not read.
     */
    private static final Set<Declared> IMPORTABLE = Set.of(JACKSON_BOM, JUNIT_BOM);

    /**
     * Maven's own options file, beside the parent pom. Its {@code -D} overrides the poms' properties; the properties of
     * {@code .mvn/jvm.config} rank below them, so that file is not read.
     */
    private static final String MAVEN_CONFIG = ".mvn/maven.config";

    /**
     * Per version of the catalog, the library of the parent pom's {@code dependencyManagement} whose version it is: the
     * BOM for Jackson and JUnit, which manages the versions of the rest of each.
     */
    private static final Map<String, Declared> FROM_PARENT_POM = new TreeMap<>(Map.of(
            "jackson", JACKSON_BOM,
            "slf4j", new Declared("org.slf4j", "slf4j-api"),
            "junit", JUNIT_BOM,
            "assertj", new Declared("org.assertj", "assertj-core"),
            "archunit", new Declared("com.tngtech.archunit", "archunit")));

    /**
     * Groups outside a library's own that its version also manages: Jackson 3's BOM manages the 2.x
     * {@code com.fasterxml.jackson.core:jackson-annotations}.
     */
    private static final Map<Declared, List<String>> EXTRA_GROUPS = Map.of(
            new Declared("tools.jackson", "jackson-bom"), List.of("com.fasterxml.jackson"));

    /**
     * The properties {@code engine/pom.xml} may set. Maven lets a property of a module override the parent's, such as
     * {@code jackson.version}, which Android does not see, so the list is short.
     */
    private static final Set<String> ENGINE_PROPERTIES = Set.of("maven.compiler.release");

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
     * the parent's {@code dependencyManagement} in those groups (and in {@code com.fasterxml.jackson}, which Jackson's
     * BOM manages too), unless it takes its version from the same property as the entry of its group; no BOM import
     * but those of Jackson's and JUnit's, at the top level; no {@code dependencies} of the parent, which the engine
     * inherits, at the top level or in a profile; no profile of the parent that sets one of the properties those
     * versions name or has a managed entry in those groups; no {@code -D} of one of those properties in
     * {@code .mvn/maven.config}; no {@code <parent>} of the parent pom, whose {@code dependencyManagement} Maven
     * would apply; and in {@code engine/pom.xml}, which sits beside the parent pom, no dependency
     * with a {@code <version>} of its own, no {@code dependencyManagement}, no property but
     * {@code maven.compiler.release}, and no profile with properties, dependencies or a {@code dependencyManagement}.
     * No versions are compared.
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
        var options = new TreeMap<File, byte[]>();
        var configFile = parentPom.toPath().resolveSibling(MAVEN_CONFIG).toFile();
        var config = getProviders().fileContents(getObjects().fileProperty().fileValue(configFile)).getAsBytes()
                .getOrNull();
        if (config != null) {
            options.put(configFile, config);
        }
        try {
            checkNothingElseIsPinned(parent, parentPom, engine, enginePom, options);
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

    private static void checkNothingElseIsPinned(byte[] parent, File parentPom, byte[] engine, File enginePom,
            Map<File, byte[]> options) throws IOException {
        if (MavenPoms.hasParent(parent, parentPom.toString())) {
            throw new GradleException("Android does not read what a parent of " + parentPom + " manages: Maven would"
                    + " apply the versions in its dependencyManagement, so the pom may have no <parent>.");
        }
        var managed = MavenPoms.managedLibraries(parent, parentPom.toString());
        if (!MavenPoms.ownDependencies(parent, parentPom.toString()).isEmpty()) {
            throw new GradleException("Android does not read the dependencies of " + parentPom + ": the engine"
                    + " inherits them, with versions Android does not take. Declare a dependency in the module that"
                    + " uses it, with its version in the dependencyManagement.");
        }
        for (var entry : managed) {
            if (entry.imported()) {
                checkImportable(entry.library(), parentPom, null);
                continue;
            }
            if (FROM_PARENT_POM.containsValue(entry.library())) {
                continue;
            }
            for (var own : FROM_PARENT_POM.values()) {
                var ownEntry = managed.stream().filter(m -> m.library().equals(own)).findFirst().orElse(null);
                if (ownEntry != null && guards(own, entry.library())
                        && !Objects.equals(entry.version(), ownEntry.version())) {
                    throw new GradleException("Android does not read the version of " + entry.library().module()
                            + " that " + parentPom + " manages: the Android build takes only the versions of "
                            + readModules() + " from it. Let it take its version from " + ownEntry.version()
                            + ", as " + own.module() + " does, or have MavenVersions read it.");
                }
            }
        }
        var versionProperties = new HashSet<String>();
        for (var entry : managed) {
            if (FROM_PARENT_POM.containsValue(entry.library())) {
                versionProperties.addAll(MavenPoms.propertiesNamedBy(entry.version()));
            }
        }
        for (var file : options.entrySet()) {
            for (var name : propertiesSet(file.getValue())) {
                if (versionProperties.contains(name)) {
                    throw new GradleException("Android does not read the property " + name + " that "
                            + file.getKey() + " sets: it is a version of " + readModules() + ", which the Android"
                            + " build takes from the parent pom's properties.");
                }
            }
        }
        for (var profile : MavenPoms.profiles(parent, parentPom.toString())) {
            if (!profile.dependencies().isEmpty()) {
                throw new GradleException("Android does not read the dependencies of profile " + profile.id()
                        + " of " + parentPom + ": the engine inherits them, with versions Android does not take.");
            }
            for (var imported : profile.managed()) {
                if (imported.imported()) {
                    checkImportable(imported.library(), parentPom, profile.id());
                    throw new GradleException("Android does not read the versions that the BOM "
                            + imported.library().module() + " manages when profile " + profile.id() + " of "
                            + parentPom + " imports it: BOMs are imported at the top level of the parent only.");
                }
            }
            for (var name : profile.propertyNames()) {
                if (versionProperties.contains(name)) {
                    throw new GradleException("Android does not read the property " + name + " that profile "
                            + profile.id() + " of " + parentPom + " sets: it is a version of " + readModules()
                            + ", which the Android build takes from the top level of the parent only.");
                }
            }
            var pinned = new ArrayList<Declared>(profile.dependencies());
            profile.managed().forEach(m -> pinned.add(m.library()));
            for (var library : pinned) {
                if (FROM_PARENT_POM.values().stream().anyMatch(own -> guards(own, library))) {
                    throw new GradleException("Android does not read the version of " + library.module()
                            + " that profile " + profile.id() + " of " + parentPom + " pins: the Android build takes"
                            + " only the versions of " + readModules() + ", from the top level of the parent only.");
                }
            }
        }
        checkEngine(engine, enginePom, parentPom);
    }

    private static void checkImportable(Declared bom, File parentPom, String profile) {
        if (!IMPORTABLE.contains(bom)) {
            throw new GradleException("Android does not read the versions that the BOM " + bom.module() + " manages,"
                    + " which " + (profile == null ? "" : "profile " + profile + " of ") + parentPom + " imports:"
                    + " the first import that manages a library wins in Maven, so the parent may import only "
                    + IMPORTABLE.stream().map(Declared::module).sorted().collect(Collectors.joining(" and "))
                    + ".");
        }
    }

    /** The names of the properties that Maven option lines such as {@code -Djackson.version=3.1} set. */
    private static List<String> propertiesSet(byte[] options) {
        var names = new ArrayList<String>();
        var words = new String(options, StandardCharsets.UTF_8).replaceAll("[\"']", "").trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String definition = null;
            if (words[i].equals("-D") || words[i].equals("--define")) {
                definition = i + 1 < words.length ? words[++i] : null;
            } else if (words[i].startsWith("--define=")) {
                definition = words[i].substring("--define=".length());
            } else if (words[i].startsWith("-D")) {
                definition = words[i].substring(2);
            }
            if (definition != null) {
                names.add(definition.split("=", 2)[0]);
            }
        }
        return names;
    }

    private static void checkEngine(byte[] engine, File enginePom, File parentPom) throws IOException {
        var name = enginePom.toString();
        var pinned = MavenPoms.librariesWithOwnVersion(engine, name);
        if (!pinned.isEmpty()) {
            throw new GradleException("Android does not read the version that " + enginePom + " gives "
                    + pinned.get(0).module() + ": a version belongs in the dependencyManagement of " + parentPom
                    + ", and the Android build reads only some of those.");
        }
        if (MavenPoms.hasDependencyManagement(engine, name)) {
            throw new GradleException("Android does not read the dependencyManagement of " + enginePom
                    + ": the versions belong in the dependencyManagement of " + parentPom + ".");
        }
        for (var property : MavenPoms.propertyNames(engine, name)) {
            if (!ENGINE_PROPERTIES.contains(property)) {
                throw new GradleException("Android does not read the property " + property + " that " + enginePom
                        + " sets: Maven would use it for the versions the parent manages, so only "
                        + String.join(", ", ENGINE_PROPERTIES) + " is allowed there.");
            }
        }
        for (var profile : MavenPoms.profiles(engine, name)) {
            if (!profile.propertyNames().isEmpty() || !profile.dependencies().isEmpty()
                    || !profile.managed().isEmpty()) {
                throw new GradleException("Android does not read what profile " + profile.id() + " of " + enginePom
                        + " sets: a profile there may configure plugins, but carry no properties, dependencies or"
                        + " dependencyManagement.");
            }
        }
    }

    /** Whether a version the parent pom gives {@code own} is the one a library of its guarded groups takes. */
    private static boolean guards(Declared own, Declared library) {
        return Groups.isIn(library.group(), own.group())
                || EXTRA_GROUPS.getOrDefault(own, List.of()).stream().anyMatch(g -> Groups.isIn(library.group(), g));
    }

    private static String readModules() {
        return FROM_PARENT_POM.values().stream().map(Declared::module).collect(Collectors.joining(", "));
    }
}
