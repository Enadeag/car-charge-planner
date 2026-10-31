package nl.enadeag.carchargeplanner.buildlogic;

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
 * What a Maven module declares, read from its {@code pom.xml} and its parent's: the parent's properties fill in the
 * {@code ${...}} versions, and its {@code dependencyManagement} gives the versions the module leaves out, directly or
 * through an imported BOM. Only what the drift check needs: no profiles, no inheritance beyond the one parent.
 */
public final class MavenPoms {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private MavenPoms() {
    }

    /**
     * The module's libraries that the Android build must declare alike: those of its own code (any scope but
     * {@code test}), and those of its tests whose group is listed, with the parent's BOMs.
     *
     * @param parentPom the parent's {@code pom.xml}, with the properties and the {@code dependencyManagement}
     * @param modulePom the module's {@code pom.xml}
     * @param testGroups the groups of the test libraries to include, such as {@code org.junit.jupiter}; a library of
     *     a subgroup counts too; empty for none
     * @return the libraries, each with the version the module or the parent gives it or null when a BOM manages it,
     *     and the parent's imported BOMs
     * @throws IOException when a file cannot be read or is not a pom
     */
    public static List<Declared> read(Path parentPom, Path modulePom, Set<String> testGroups) throws IOException {
        var parent = parse(parentPom);
        var properties = new HashMap<String, String>();
        for (var section : children(parent.getDocumentElement(), "properties")) {
            for (var property : children(section, null)) {
                properties.put(property.getTagName(), property.getTextContent().trim());
            }
        }
        var declared = new ArrayList<Declared>();
        var managed = new HashMap<String, String>();
        for (var dependency : dependencies(parent.getDocumentElement(), "dependencyManagement")) {
            var library = library(dependency, properties);
            if ("import".equals(text(dependency, "scope"))) {
                declared.add(new Declared(library.group(), library.artifact(), library.version(), true));
            } else if (library.version() != null) {
                managed.put(library.module(), library.version());
            }
        }
        for (var dependency : dependencies(parse(modulePom).getDocumentElement(), null)) {
            var library = library(dependency, properties);
            boolean ofTests = "test".equals(text(dependency, "scope"));
            if (ofTests && testGroups.stream().noneMatch(group -> inGroup(library.group(), group))) {
                continue;
            }
            var version = library.version() != null ? library.version() : managed.get(library.module());
            declared.add(new Declared(library.group(), library.artifact(), version, false));
        }
        return declared;
    }

    /**
     * Whether a group is the given one or a subgroup of it.
     *
     * @param group the library's group
     * @param parent the group it may be in
     * @return true when {@code group} is {@code parent} or starts with {@code parent.}
     */
    static boolean inGroup(String group, String parent) {
        return group.equals(parent) || group.startsWith(parent + ".");
    }

    private static Declared library(Element dependency, Map<String, String> properties) {
        var version = text(dependency, "version");
        return new Declared(text(dependency, "groupId"), text(dependency, "artifactId"),
                version == null ? null : resolve(version, properties), false);
    }

    /** Fills in the properties; one that is not known stays as it is, so it shows as a difference. */
    private static String resolve(String value, Map<String, String> properties) {
        Matcher matcher = PROPERTY.matcher(value);
        var resolved = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(properties.getOrDefault(matcher.group(1), matcher.group())));
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

    private static Document parse(Path pom) throws IOException {
        try (InputStream in = Files.newInputStream(pom)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(in);
            if (!"project".equals(document.getDocumentElement().getTagName())) {
                throw new IOException(pom + " is not a Maven pom");
            }
            return document;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(pom + " could not be read as XML: " + e.getMessage(), e);
        }
    }
}
