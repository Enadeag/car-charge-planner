package io.github.enadeag.carchargeplanner.engine.config;

/**
 * A value in the config file that the owner sets, so that a client can show a problem with it next to the place where
 * it is entered.
 */
public enum Setting {
    /** The minimum charge level of the battery. */
    MINIMUM_CHARGE,
    /** The target charge level of the battery. */
    TARGET_CHARGE,
    /** The share of the forecast surplus the plan counts on. */
    PLANNING_MARGIN,
    /** The grid energy a charge window may take. */
    IMPORT_TOLERANCE,
    /** The shortest charge window. */
    MINIMUM_WINDOW,
    /** How long before a charge window the owner is told. */
    NOTIFY_LEAD
}
