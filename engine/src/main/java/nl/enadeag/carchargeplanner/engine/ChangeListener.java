package nl.enadeag.carchargeplanner.engine;

/**
 * Told by the {@link Engine} after each change of what it stores that the plan depends on: the config, the API keys,
 * the battery level, the away periods and the trips. It carries nothing: something changed, so whoever listens reads
 * what is stored now. Each app adds its listeners once, where it puts its parts together.
 */
@FunctionalInterface
public interface ChangeListener {

    /**
     * Something the plan depends on was changed, or a change was tried and failed, which may have left part of it
     * stored. Runs on the thread that made the change, right after it, so it must not block: hand slow work, such as
     * a planning round, on to a thread of its own.
     */
    void changed();
}
