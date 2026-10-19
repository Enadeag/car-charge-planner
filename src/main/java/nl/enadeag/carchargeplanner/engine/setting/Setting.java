package nl.enadeag.carchargeplanner.engine.setting;

/**
 * A value in the config file that the owner sets, so that a client can show a problem with it next to the place where
 * it is entered.
 */
public enum Setting {
    /** The latitude of the home. */
    LATITUDE,
    /** The longitude of the home. */
    LONGITUDE,
    /** The size of a panel array in kWp. */
    PANEL_SIZE,
    /** The tilt of a panel array. */
    PANEL_TILT,
    /** The direction a panel array faces. */
    PANEL_DIRECTION,
    /** The share of a panel array's power that is lost. */
    PANEL_LOSSES,
    /** The power the house uses on its own. */
    HOUSE_BASELOAD,
    /** The usable capacity of the car's battery. */
    CAR_BATTERY,
    /** The energy the car uses per distance. */
    CAR_CONSUMPTION,
    /** The distance the car is driven on a typical day. */
    CAR_DAILY_DISTANCE,
    /** The phases the charger charges on. */
    CHARGER_PHASES,
    /** The lowest current per phase the charger and the car accept. */
    CHARGER_MIN_CURRENT,
    /** The highest current per phase the charger, the car and the home connection allow. */
    CHARGER_MAX_CURRENT,
    /** The voltage at the charger. */
    CHARGER_VOLTAGE,
    /** The share of the energy from the wall that ends up in the battery. */
    CHARGER_EFFICIENCY,
    /** The primary forecast provider. */
    PRIMARY_PROVIDER,
    /** The fallback forecast provider. */
    FALLBACK_PROVIDER,
    /** A weekly away period. */
    AWAY_PERIOD,
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
