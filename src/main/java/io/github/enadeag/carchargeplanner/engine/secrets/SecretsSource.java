package io.github.enadeag.carchargeplanner.engine.secrets;

/** Where the forecast service gets the providers' secret settings from. */
@FunctionalInterface
public interface SecretsSource {

    Secrets load() throws SecretsProblem;
}
