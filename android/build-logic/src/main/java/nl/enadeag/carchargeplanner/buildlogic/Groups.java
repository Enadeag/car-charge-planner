package nl.enadeag.carchargeplanner.buildlogic;

/**
 * The one rule for Maven groups that the checks share: a group holds itself and its subgroups, such as
 * {@code org.junit.jupiter} in {@code org.junit}, but not a group that only starts alike, such as {@code org.junitx}.
 */
public final class Groups {

    private Groups() {
    }

    /**
     * Whether a group is the given one or a subgroup of it.
     *
     * @param group the library's group; never null
     * @param parent the group it may be in; never null
     * @return true when {@code group} is {@code parent} or starts with {@code parent.}
     */
    public static boolean isIn(String group, String parent) {
        return group.equals(parent) || group.startsWith(parent + ".");
    }
}
