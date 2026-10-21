package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.UnitInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.Quantity;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalTags;
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

/**
 * The car: its usable battery, its consumption and the distance driven per day, with typical values on request.
 * Distances and consumption show in the units the owner chose, also before they are saved; a typical value stays
 * tagged in other units.
 */
public final class CarCard implements ConfigPart {

    private final Messages messages;
    private final NumberInput battery;
    private final UnitInput consumption;
    private final UnitInput distance;
    private final ProblemRows problems;
    private final TypicalTags typical;
    private final CardView card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     */
    public CarCard(Messages messages) {
        this.messages = messages;
        battery = new NumberInput(new NumberRange(1, 200, 1, 1), "kWh", messages);
        consumption = new UnitInput(Quantity.CONSUMPTION, messages);
        distance = new UnitInput(Quantity.DISTANCE, messages);
        problems = new ProblemRows(messages);
        typical = new TypicalTags(messages.text(TextKey.TYPICAL_TAG));
        card = new CardView(messages.text(TextKey.CARD_CAR), messages.text(TextKey.CARD_CAR_HINT),
                row(ConfigSetting.CAR_BATTERY, TextKey.FIELD_CAR_BATTERY, TextKey.FIELD_CAR_BATTERY_HINT, battery),
                row(ConfigSetting.CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION_HINT,
                        consumption),
                row(ConfigSetting.CAR_DAILY_DISTANCE, TextKey.FIELD_CAR_DISTANCE, TextKey.FIELD_CAR_DISTANCE_HINT,
                        distance),
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_CAR), messages));
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    public void showIn(Units units) {
        typical.keepTagsWhile(() -> {
            consumption.showIn(units);
            distance.showIn(units);
        });
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    public CardView view() {
        return card;
    }

    @Override
    public void load(EngineConfig config) {
        var car = config.system().car();
        typical.load(config.typical().system(), () -> {
            battery.load(car.usableBatteryKwh());
            consumption.load(car.consumptionKwhPer100km());
            distance.load(car.kmPerDay());
        });
        problems.clear();
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
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
        return car(Car.STANDARD_TRIP_SURCHARGE, problems::show).isEmpty();
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
     * The car as entered, with a trip surcharge this card does not show, after its record checked it; a refused value
     * goes to {@code refused}.
     */
    private Optional<Car> car(Percent tripSurcharge, Consumer<InvalidSetting> refused) {
        try {
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
        return List.of(battery, consumption, distance);
    }

    private FieldRow row(ConfigSetting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        typical.follow(setting, row::showTag);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}
