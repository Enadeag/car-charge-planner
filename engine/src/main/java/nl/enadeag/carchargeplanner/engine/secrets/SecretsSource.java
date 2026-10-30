package nl.enadeag.carchargeplanner.engine.secrets;

/** Where the forecast service gets the providers' secret settings from. */
@FunctionalInterface
public interface SecretsSource {

    /**
     * Reads the stored secret settings.
     *
     * @return every provider's secret settings; none when nothing is stored
     * @throws SecretsProblem when they are stored but cannot be read
     */
    Secrets load() throws SecretsProblem;
}
