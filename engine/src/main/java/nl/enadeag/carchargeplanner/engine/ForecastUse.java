package nl.enadeag.carchargeplanner.engine;

/** Where a planning round may get its forecast from. */
public enum ForecastUse {

    /** From the stored forecast or a provider, as the forecast service's rules allow. */
    ASK_PROVIDERS,

    /**
     * From the stored forecast only: no provider is asked, so the network is not used, and no API key is read, such as
     * for a round at a moment the device woke for.
     */
    STORED_ONLY
}
