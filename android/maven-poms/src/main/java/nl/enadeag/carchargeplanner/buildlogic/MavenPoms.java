package nl.enadeag.carchargeplanner.buildlogic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * {@code dependencyManagement} gives a library, with the parent's properties filled in. No profiles, no inheritance
 * beyond the one parent.
 */
public final class MavenPoms {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

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
     * @throws IllegalArgumentException when the pom does not manage the library, gives it no version, or its version
     *     names a property the pom does not set; the message names the library or the property, and the pom
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
        return matcher.appendTail(resolved).toString();
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
