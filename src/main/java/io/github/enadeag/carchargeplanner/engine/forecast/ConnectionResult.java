package io.github.enadeag.carchargeplanner.engine.forecast;

/**
 * What asking a provider once came to, for set-up and Settings.
 *
 * @param worked whether the provider gave a forecast
 * @param days days of forecast, from today
 * @param message for the owner: "OK: 4 days of forecast", or what went wrong
 */
public record ConnectionResult(boolean worked, int days, String message) {
}
