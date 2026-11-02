package nl.enadeag.carchargeplanner.buildlogic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * What the Android build takes from the Maven poms: the libraries a module declares, and the version the parent's
 * {@code dependencyManagement} gives a library, with the parent's properties filled in. No inheritance beyond the one parent, and a profile is
 * only read to report what it sets, never applied. {@code managedVersion} takes the pom as bytes, not as a path, because the settings read the
 * pom as a tracked input of Gradle's configuration, which hands them its contents.
 */
public final class MavenPoms {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern PLAIN_VERSION = Pattern.compile("[0-9A-Za-z._-]+");

    private MavenPoms() {
    }

    /**
     * The module's libraries that the Android build must declare alike: those of its own code (any scope but
     * {@code test}), and those of its tests whose group is listed.
     *
     * @param modulePom the module's {@code pom.xml}
     * @param testGroups the groups of the test libraries to include, such as {@code org.junit.jupiter}; a library of
     *     a subgroup counts too; empty for none
     * @return the libraries, in the pom's order
     * @throws IOException when the file cannot be read or is not a pom
     */
    public static List<Declared> libraries(Path modulePom, Set<String> testGroups) throws IOException {
        Document module;
        try (InputStream in = Files.newInputStream(modulePom)) {
            module = parse(in, modulePom.toString());
        }
        var declared = new ArrayList<Declared>();
        for (var dependency : dependencies(module.getDocumentElement(), null)) {
            var library = new Declared(text(dependency, "groupId"), text(dependency, "artifactId"));
            boolean ofTests = "test".equals(text(dependency, "scope"));
            if (!ofTests || testGroups.stream().anyMatch(group -> Groups.isIn(library.group(), group))) {
                declared.add(library);
            }
        }
        return declared;
    }

    /**
     * The version a pom's {@code dependencyManagement} gives a library, such as the parent's version of
     * {@code tools.jackson:jackson-bom}, with the pom's properties filled in.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @param library the library, as its {@code dependencyManagement} entry names it
     * @return the version, never blank
     * @throws IOException when the contents are not a pom
     * @throws IllegalArgumentException when the pom does not manage the library, gives it no version, its version
     *     names a property the pom does not set, or the result is not a plain version (letters, digits, {@code .},
     *     {@code _} and {@code -}: no range, {@code +}, {@code latest.*} or property inside a property); the message
     *     names the library or the property, and the pom
     */
    public static String managedVersion(byte[] pom, String pomName, Declared library) throws IOException {
        var root = parse(new ByteArrayInputStream(pom), pomName).getDocumentElement();
        var properties = new HashMap<String, String>();
        for (var section : children(root, "properties")) {
            for (var property : children(section, null)) {
                properties.put(property.getTagName(), property.getTextContent().trim());
            }
        }
        for (var dependency : dependencies(root, "dependencyManagement")) {
            if (library.group().equals(text(dependency, "groupId"))
                    && library.artifact().equals(text(dependency, "artifactId"))) {
                var version = text(dependency, "version");
                if (version == null || version.isEmpty()) {
                    throw new IllegalArgumentException(library.module() + " has no version in the dependencyManagement"
                            + " of " + pomName);
                }
                return resolve(version, properties, library, pomName);
            }
        }
        throw new IllegalArgumentException(pomName + " has no " + library.module() + " in its dependencyManagement");
    }

    /**
     * A library a pom's {@code dependencyManagement} manages.
     *
     * @param library the library
     * @param version the version as the pom writes it, a property reference such as {@code ${slf4j.version}} included;
     *     null when the entry has none
     * @param imported whether the entry imports a BOM ({@code <scope>import</scope>}) rather than managing one library
     */
    public record Managed(Declared library, String version, boolean imported) {
    }

    /**
     * The entries of a pom's {@code dependencyManagement}, with their versions as written.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return the entries, in the pom's order
     * @throws IOException when the contents are not a pom
     */
    public static List<Managed> managedLibraries(byte[] pom, String pomName) throws IOException {
        return managed(parse(new ByteArrayInputStream(pom), pomName).getDocumentElement(), "dependencyManagement");
    }

    /**
     * What a profile of a pom sets or gives that reaches the versions of the libraries.
     *
     * @param id the profile's {@code id}, or {@code (no id)}
     * @param propertyNames the names of the properties it sets
     * @param dependencies the libraries of its {@code dependencies}
     * @param managed the entries of its {@code dependencyManagement}
     */
    public record Profile(String id, List<String> propertyNames, List<Declared> dependencies, List<Managed> managed) {
    }

    /**
     * The profiles of a pom.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return the profiles, in the pom's order
     * @throws IOException when the contents are not a pom
     */
    public static List<Profile> profiles(byte[] pom, String pomName) throws IOException {
        var root = parse(new ByteArrayInputStream(pom), pomName).getDocumentElement();
        var found = new ArrayList<Profile>();
        for (var list : children(root, "profiles")) {
            for (var profile : children(list, "profile")) {
                var id = text(profile, "id");
                var libraries = new ArrayList<Declared>();
                for (var dependency : dependencies(profile, null)) {
                    libraries.add(new Declared(text(dependency, "groupId"), text(dependency, "artifactId")));
                }
                found.add(new Profile(id == null || id.isEmpty() ? "(no id)" : id, propertyNames(profile),
                        libraries, managed(profile, "dependencyManagement")));
            }
        }
        return found;
    }

    /**
     * The names of the properties a pom sets at its top level, not those of its profiles.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return the names, in the pom's order
     * @throws IOException when the contents are not a pom
     */
    public static List<String> propertyNames(byte[] pom, String pomName) throws IOException {
        return propertyNames(parse(new ByteArrayInputStream(pom), pomName).getDocumentElement());
    }

    /**
     * Whether a pom has a {@code dependencyManagement} of its own.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return true when the top level has the element, even an empty one
     * @throws IOException when the contents are not a pom
     */
    public static boolean hasDependencyManagement(byte[] pom, String pomName) throws IOException {
        var root = parse(new ByteArrayInputStream(pom), pomName).getDocumentElement();
        return !children(root, "dependencyManagement").isEmpty();
    }

    /**
     * Whether a pom has a {@code <parent>} of its own.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return true when the top level has the element
     * @throws IOException when the contents are not a pom
     */
    public static boolean hasParent(byte[] pom, String pomName) throws IOException {
        var root = parse(new ByteArrayInputStream(pom), pomName).getDocumentElement();
        return !children(root, "parent").isEmpty();
    }

    /**
     * The properties a version names.
     *
     * @param version a version as a pom writes it, such as {@code ${jackson.version}}; null for none
     * @return the names inside {@code ${}}, such as {@code jackson.version}; empty when there are none
     */
    public static List<String> propertiesNamedBy(String version) {
        var names = new ArrayList<String>();
        if (version != null) {
            Matcher matcher = PROPERTY.matcher(version);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static List<String> propertyNames(Element holder) {
        var names = new ArrayList<String>();
        for (var section : children(holder, "properties")) {
            for (var property : children(section, null)) {
                names.add(property.getTagName());
            }
        }
        return names;
    }

    private static List<Managed> managed(Element holder, String section) {
        var managed = new ArrayList<Managed>();
        for (var dependency : dependencies(holder, section)) {
            managed.add(new Managed(new Declared(text(dependency, "groupId"), text(dependency, "artifactId")),
                    text(dependency, "version"), "import".equals(text(dependency, "scope"))));
        }
        return managed;
    }

    /**
     * The libraries of a pom's own {@code dependencies}, at the top level: not those of its profiles, and not the
     * ones plugins have under {@code build}.
     *
     * @param pom the pom's contents
     * @param pomName the pom's path, for the messages
     * @return the libraries, in the pom's order
     * @throws IOException when the contents are not a pom
     */
    public static List<Declared> ownDependencies(byte[] pom, String pomName) throws IOException {
        var root = parse(new ByteArrayInputStream(pom), pomName).getDocumentElement();
        var found = new ArrayList<Declared>();
        for (var dependency : dependencies(root, null)) {
            found.add(new Declared(text(dependency, "groupId"), text(dependency, "artifactId")));
        }
        return found;
    }

    /**
     * The libraries a module's own {@code dependencies} give a {@code <version>} of their own, instead of taking the
     * parent's.
     *
     * @param modulePom the module pom's contents
     * @param pomName the pom's path, for the messages
     * @return those libraries, in the pom's order; empty when none sets a version
     * @throws IOException when the contents are not a pom
     */
    public static List<Declared> librariesWithOwnVersion(byte[] modulePom, String pomName) throws IOException {
        var root = parse(new ByteArrayInputStream(modulePom), pomName).getDocumentElement();
        var found = new ArrayList<Declared>();
        for (var dependency : dependencies(root, null)) {
            if (text(dependency, "version") != null) {
                found.add(new Declared(text(dependency, "groupId"), text(dependency, "artifactId")));
            }
        }
        return found;
    }

    private static String resolve(String value, Map<String, String> properties, Declared library, String pomName) {
        Matcher matcher = PROPERTY.matcher(value);
        var resolved = new StringBuilder();
        while (matcher.find()) {
            var property = properties.get(matcher.group(1));
            if (property == null || property.isEmpty()) {
                throw new IllegalArgumentException(pomName + " does not set the property " + matcher.group(1)
                        + ", which the version of " + library.module() + " names");
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(property));
        }
        var version = matcher.appendTail(resolved).toString();
        if (version.contains("${")) {
            throw new IllegalArgumentException("the version of " + library.module() + " in " + pomName + " is \""
                    + version + "\" once its properties are filled in: a property that names another property is not"
                    + " resolved");
        }
        var lower = version.toLowerCase(Locale.ROOT);
        if (!PLAIN_VERSION.matcher(version).matches() || lower.startsWith("latest") || lower.equals("release")) {
            throw new IllegalArgumentException("the version of " + library.module() + " in " + pomName + " is \""
                    + version + "\", which is not a plain version: Android takes letters, digits, '.', '_' and '-'"
                    + " only, no range, '+' or 'latest'");
        }
        return version;
    }

    /** The {@code dependency} elements of the root's {@code dependencies}, or of that within {@code section}. */
    private static List<Element> dependencies(Element root, String section) {
        var within = section == null ? List.of(root) : children(root, section);
        var found = new ArrayList<Element>();
        for (var outer : within) {
            for (var list : children(outer, "dependencies")) {
                found.addAll(children(list, "dependency"));
            }
        }
        return found;
    }

    /** The element children of a node, all of them or those of one name. */
    private static List<Element> children(Element parent, String name) {
        var found = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && (name == null || element.getTagName().equals(name))) {
                found.add(element);
            }
        }
        return found;
    }

    /** The trimmed text of a child element, or null when there is none. */
    private static String text(Element parent, String name) {
        var found = children(parent, name);
        return found.isEmpty() ? null : found.get(0).getTextContent().trim();
    }

    private static Document parse(InputStream in, String pomName) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(in);
            if (!"project".equals(document.getDocumentElement().getTagName())) {
                throw new IOException(pomName + " is not a Maven pom");
            }
            return document;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(pomName + " could not be read as XML: " + e.getMessage(), e);
        }
    }
}
