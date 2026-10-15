package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numbers as the services want them in an address: "35" rather than "35.0". */
final class Decimals {

    private Decimals() {
    }

    /**
     * A number as short as it can be written exactly.
     *
     * @param value any finite number
     * @return such as "35" for 35.0 and "4.9" for 4.9
     */
    static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * A number rounded half to even, without trailing zeros.
     *
     * @param value any finite number
     * @param places the most decimals to keep, 0 or more
     * @return such as "52.3712" for 52.37123456 with 4 places
     */
    static String rounded(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }
}
