package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.UnitInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.Limit;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.Quantity;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.settings.page.TypicalButton;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * The car: its usable battery, its consumption and its daily distance, with typical values on request, and in Settings
 * the trip surcharge, which the page folds away ({@link #folded()}). Distances and consumption show in the units the
 * owner chose, also before they are saved; a typical value stays typical in other units. Set-up leaves the trip
 * surcharge out, and the card then keeps the one stored.
 */
public final class CarCard implements ConfigPart {

    private final Messages messages;
    private final NumberInput battery;
    private final UnitInput consumption;
    private final UnitInput distance;
    private final NumberInput surcharge;
    private final boolean showsSurcharge;
    private final FieldRow surchargeRow;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final CardView card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     * @param showsSurcharge whether the card has the trip surcharge field; without it the stored surcharge is kept
     */
    public CarCard(Messages messages, boolean showsSurcharge) {
        this.messages = messages;
        this.showsSurcharge = showsSurcharge;
        battery = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.CAR_BATTERY), 1, 1), "kWh", messages);
        consumption = new UnitInput(Quantity.CONSUMPTION, messages);
        distance = new UnitInput(Quantity.DISTANCE, messages);
        surcharge = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.CAR_TRIP_SURCHARGE), 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks();
        // no typical value
        surchargeRow = FieldRow.of(messages, TextKey.FIELD_CAR_TRIP_SURCHARGE, TextKey.FIELD_CAR_TRIP_SURCHARGE_HINT,
                surcharge);
        if (showsSurcharge) {
            problems.add(ConfigSetting.CAR_TRIP_SURCHARGE, surchargeRow);
        }
        card = new CardView(messages.text(TextKey.CARD_CAR), messages.text(TextKey.CARD_CAR_HINT),
                row(ConfigSetting.CAR_BATTERY, TextKey.FIELD_CAR_BATTERY, TextKey.FIELD_CAR_BATTERY_HINT, battery),
                row(ConfigSetting.CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION_HINT,
                        consumption),
                row(ConfigSetting.CAR_DAILY_DISTANCE, TextKey.FIELD_CAR_DAILY_DISTANCE,
                        TextKey.FIELD_CAR_DAILY_DISTANCE_HINT, distance),
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_CAR), messages));
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    public void showIn(Units units) {
        typical.keepMarksWhile(() -> {
            consumption.showIn(units);
            distance.showIn(units);
        });
    }

    /**
     * The card itself: the battery, the consumption, the daily distance and the typical values' button.
     *
     * @return the same card every time
     */
    public CardView view() {
        return card;
    }

    /**
     * What the page shows of the car under its "More settings" fold, outside the card: the trip surcharge's row in
     * Settings. The card keeps reading and checking it.
     *
     * @return the trip surcharge's row; empty in set-up, which leaves the trip surcharge out
     */
    public List<Node> folded() {
        return showsSurcharge ? List.of(surchargeRow) : List.of();
    }

    @Override
    public void load(EngineConfig config) {
        var car = config.system().car();
        typical.load(config.typical().system(), () -> {
            battery.load(car.usableBatteryKwh());
            consumption.load(car.consumptionKwhPer100km());
            distance.load(car.kmPerDay());
        });
        surcharge.load(car.tripSurcharge().value());
        problems.clear();
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
        }
        if (problems.typingIsWrong()) {
            return Optional.empty();
        }
        var system = config.system();
        return car(system.car().tripSurcharge(), _ -> { }).map(car -> {
            return config.withSystem(system.withHouseAndCar(system.house(), car),
                    new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels()));
        });
    }

    @Override
    public boolean showProblems() {
        problems.clear();
        if (!isChanged()) {
            return false;
        }
        inputs().forEach(Input::commitTyped);
        return problems.showTyping() || car(Car.STANDARD_TRIP_SURCHARGE, problems::show).isEmpty();
    }

    /**
     * Whether a field the page folds away shows a problem, so the page can open its fold.
     *
     * @return true when the trip surcharge's field shows one
     */
    public boolean foldedShowsProblem() {
        return problems.shows(ConfigSetting.CAR_TRIP_SURCHARGE);
    }

    @Override
    public void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    /**
     * The car as entered, after its record checked it; a refused value goes to {@code refused}.
     *
     * @param stored the trip surcharge to keep when the card does not show the field
     */
    private Optional<Car> car(Percent stored, Consumer<InvalidSetting> refused) {
        try {
            var tripSurcharge = showsSurcharge
                    ? Percent.of(ConfigSetting.CAR_TRIP_SURCHARGE, surcharge.value()) : stored;
            return Optional.of(new Car(battery.value(), consumption.value(), distance.value(), tripSurcharge));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private void useTypical() {
        typical.useTypical(() -> {
            battery.set(Car.TYPICAL.usableBatteryKwh());
            consumption.set(Car.TYPICAL.consumptionKwhPer100km());
            distance.set(Car.TYPICAL.kmPerDay());
        });
    }

    private List<Input> inputs() {
        return showsSurcharge ? List.of(battery, consumption, distance, surcharge)
                : List.of(battery, consumption, distance);
    }

    private FieldRow row(ConfigSetting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        typical.follow(setting);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}
