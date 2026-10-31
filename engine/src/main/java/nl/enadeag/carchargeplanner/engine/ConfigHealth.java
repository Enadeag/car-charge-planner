package nl.enadeag.carchargeplanner.engine;

import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether the config file could be planned with the last time, so that a config that becomes unusable, one whose
 * problem changes, and one that is usable again are logged once rather than at every planning round. Safe to use from
 * several threads.
 */
final class ConfigHealth {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigHealth.class);

    /** The problem logged last; null while the config is usable, as it is taken to be at the start. */
    private String problem;

    /** Starts taking the config to be usable, which is not logged. */
    ConfigHealth() {
    }

    /** A plan was made with the config; logged when it could not be used before. */
    synchronized void markUsable() {
        if (problem != null) {
            LOG.info("the config file can be used again");
            problem = null;
        }
    }

    /**
     * The config could not be used; logged when it is another problem than the one logged last. A missing file, as
     * before set-up, is not a fault, so it is logged as information.
     *
     * @param found the problem, with its kind, the file and the engine's detail, which never holds a value of a
     *     setting
     */
    synchronized void markUnusable(ConfigProblem found) {
        var text = found.kind() == ConfigProblem.Kind.MISSING ? "there is no config file at " + found.file()
                : "the config file " + found.file() + " cannot be used (" + found.kind() + "): " + found.detail();
        if (text.equals(problem)) {
            return;
        }
        problem = text;
        if (found.kind() == ConfigProblem.Kind.MISSING) {
            LOG.info(text);
        } else {
            LOG.warn(text);
        }
    }
}
