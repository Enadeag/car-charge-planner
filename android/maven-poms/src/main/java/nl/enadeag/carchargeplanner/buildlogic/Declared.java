package nl.enadeag.carchargeplanner.buildlogic;

import java.io.Serializable;

/**
 * A library one build declares, by group and artifact.
 *
 * @param group the group, such as {@code tools.jackson.core}
 * @param artifact the artifact, such as {@code jackson-databind}
 */
public record Declared(String group, String artifact) implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Checks the names.
     *
     * @throws IllegalArgumentException when the group or the artifact is blank
     */
    public Declared {
        if (group == null || group.isBlank() || artifact == null || artifact.isBlank()) {
            throw new IllegalArgumentException("group and artifact must not be blank: " + group + ":" + artifact);
        }
    }

    /**
     * The library's name.
     *
     * @return {@code group:artifact}
     */
    public String module() {
        return group + ":" + artifact;
    }
}
