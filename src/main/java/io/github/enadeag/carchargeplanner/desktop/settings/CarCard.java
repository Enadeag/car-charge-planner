package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.app.Units;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.Car;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The car: its usable battery, its consumption and the distance driven per day, with typical values on request.
 * Distances and consumption show in the units the owner chose, also before they are saved; a typical value stays
 * tagged in other units.
 */
final class CarCard implements ConfigPart {

    private static final List<Setting> OWN = List.of(Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION,
            Setting.CAR_DAILY_DISTANCE);

    private final Messages messages;
    private final NumberField battery;
    private final UnitField consumption;
    private final UnitField distance;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final Card card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     */
    CarCard(Messages messages) {
        this.messages = messages;
        battery = new NumberField(new NumberField.Range(1, 200, 1, 1), "kWh", messages);
        consumption = new UnitField(Quantity.CONSUMPTION, messages);
        distance = new UnitField(Quantity.DISTANCE, messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        card = new Card(messages.text(TextKey.CARD_CAR), messages.text(TextKey.CARD_CAR_HINT),
                row(Setting.CAR_BATTERY, TextKey.FIELD_CAR_BATTERY, TextKey.FIELD_CAR_BATTERY_HINT, battery),
                row(Setting.CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION_HINT,
                        consumption),
                row(Setting.CAR_DAILY_DISTANCE, TextKey.FIELD_CAR_DISTANCE, TextKey.FIELD_CAR_DISTANCE_HINT, distance),
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_CAR), messages));
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    void showIn(Units units) {
        typical.keep(() -> {
            consumption.showIn(units);
            distance.showIn(units);
        });
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    Card view() {
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
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        problems.clear();
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var car = new Car(battery.value(), consumption.value(), distance.value());
            var marked = new HashSet<>(config.typical().system());
            marked.removeAll(OWN);
            marked.addAll(typical.marked());
            var system = config.system();
            return Optional.of(config.withSystem(system.withHouseAndCar(system.house(), car),
                    new TypicalValues(marked, config.typical().panels())));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    @Override
    public void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
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

    private FieldRow row(Setting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        typical.follow(setting, row, input);
        return row;
    }
}
