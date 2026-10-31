package nl.enadeag.carchargeplanner.buildlogic;

import java.io.Serializable;

/**
 * A library one build declares, as the build file names it.
 *
 * @param group the group, such as {@code tools.jackson.core}
 * @param artifact the artifact, such as {@code jackson-databind}
 * @param version the version the build file gives it; null when a BOM manages it
 * @param bom whether it is a BOM (a Maven import, a Gradle platform) that manages the versions of others
 */
public record Declared(String group, String artifact, String version, boolean bom) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Checks the names.
     *
     * @throws IllegalArgumentException when the group or the artifact is blank, or a BOM has no version
     */
    public Declared {
        if (group == null || group.isBlank() || artifact == null || artifact.isBlank()) {
            throw new IllegalArgumentException("group and artifact must not be blank: " + group + ":" + artifact);
        }
        if (bom && version == null) {
            throw new IllegalArgumentException("the BOM " + group + ":" + artifact + " needs a version");
        }
    }

    /**
     * The library's name without the version.
     *
     * @return {@code group:artifact}
     */
    public String module() {
        return group + ":" + artifact;
    }
}
