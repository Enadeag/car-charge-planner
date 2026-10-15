package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numbers as the services want them in an address: "35" rather than "35.0". */
final class Decimals {

    private Decimals() {
    }

    static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String rounded(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }
}
