package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.engine.config.Car;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.House;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * The car, and the house's own use of power, each with typical values on request. Distances and consumption show in
 * the units the App page chooses, also before they are saved.
 */
final class CarHousePage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final NumberField baseload;
    private final NumberField battery;
    private final UnitField consumption;
    private final UnitField distance;
    private final ProblemRows problems;
    private final TypicalMarks houseTypical;
    private final TypicalMarks carTypical;
    private final Card house;
    private final Card car;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     */
    CarHousePage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.CAR_HOUSE.title()));
        baseload = new NumberField(new NumberField.Range(0, 20, 0.05, 2), "kW", messages);
        battery = new NumberField(new NumberField.Range(1, 200, 1, 1), "kWh", messages);
        consumption = new UnitField(Quantity.CONSUMPTION, messages);
        distance = new UnitField(Quantity.DISTANCE, messages);
        problems = new ProblemRows(messages);
        var tag = messages.text(TextKey.TYPICAL_TAG);
        houseTypical = new TypicalMarks(tag);
        carTypical = new TypicalMarks(tag);
        car = new Card(messages.text(TextKey.CARD_CAR), messages.text(TextKey.CARD_CAR_HINT),
                row(carTypical, Setting.CAR_BATTERY, TextKey.FIELD_CAR_BATTERY, TextKey.FIELD_CAR_BATTERY_HINT, battery),
                row(carTypical, Setting.CAR_CONSUMPTION, TextKey.FIELD_CAR_CONSUMPTION,
                        TextKey.FIELD_CAR_CONSUMPTION_HINT, consumption),
                row(carTypical, Setting.CAR_DAILY_DISTANCE, TextKey.FIELD_CAR_DISTANCE, TextKey.FIELD_CAR_DISTANCE_HINT,
                        distance),
                typicalButton(this::useTypicalCar));
        house = new Card(messages.text(TextKey.CARD_HOUSE), messages.text(TextKey.CARD_HOUSE_HINT),
                row(houseTypical, Setting.HOUSE_BASELOAD, TextKey.FIELD_BASELOAD, TextKey.FIELD_BASELOAD_HINT, baseload),
                typicalButton(this::useTypicalHouse));
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    void showIn(Units units) {
        consumption.showIn(units);
        distance.showIn(units);
    }

    @Override
    public Category category() {
        return Category.CAR_HOUSE;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return houseTypical.isChanged() || carTypical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        var system = config.system();
        var typical = config.typical().system();
        houseTypical.load(typical, () -> baseload.load(system.house().baseloadKw()));
        carTypical.load(typical, () -> {
            battery.load(system.car().usableBatteryKwh());
            consumption.load(system.car().consumptionKwhPer100km());
            distance.load(system.car().kmPerDay());
        });
        problems.clear();
        view.show(car, house);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(Unavailable.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        problems.clear();
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var newHouse = new House(baseload.value());
            var newCar = new Car(battery.value(), consumption.value(), distance.value());
            var typical = new HashSet<>(config.typical().system());
            typical.removeAll(List.of(Setting.HOUSE_BASELOAD, Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION,
                    Setting.CAR_DAILY_DISTANCE));
            typical.addAll(houseTypical.marked());
            typical.addAll(carTypical.marked());
            return Optional.of(config.withSystem(config.system().withHouseAndCar(newHouse, newCar),
                    new TypicalValues(typical, config.typical().panels())));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    private void useTypicalHouse() {
        houseTypical.useTypical(() -> baseload.set(House.TYPICAL.baseloadKw()));
    }

    private void useTypicalCar() {
        carTypical.useTypical(() -> {
            battery.set(Car.TYPICAL.usableBatteryKwh());
            consumption.set(Car.TYPICAL.consumptionKwhPer100km());
            distance.set(Car.TYPICAL.kmPerDay());
        });
    }

    private HBox typicalButton(Runnable action) {
        var button = new Button(messages.text(TextKey.USE_TYPICAL));
        button.setOnAction(event -> action.run());
        var line = new HBox(button);
        line.getStyleClass().add("card-buttons");
        return line;
    }

    private List<Input> inputs() {
        return List.of(baseload, battery, consumption, distance);
    }

    private FieldRow row(TypicalMarks marks, Setting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        marks.follow(setting, row, input);
        return row;
    }
}
