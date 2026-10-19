package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.Optional;
import javafx.scene.layout.Region;

/**
 * The car, and the house's own use of power, each with typical values on request. Distances and consumption show in
 * the units the App page chooses, also before they are saved.
 */
final class CarHousePage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final CarCard car;
    private final HouseCard house;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     */
    CarHousePage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.CAR_HOUSE.title()));
        car = new CarCard(messages);
        house = new HouseCard(messages);
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    void showIn(Units units) {
        car.showIn(units);
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
        car.onChange(action);
        house.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return car.isChanged() || house.isChanged();
    }

    @Override
    public void load(EngineConfig config) {
        house.load(config);
        car.load(config);
        view.show(car.view(), house.view());
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    /** Both cards are checked, so each shows its own problems. */
    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        var withCar = car.applyTo(config);
        var withBoth = house.applyTo(withCar.orElse(config));
        return withCar.isPresent() ? withBoth : Optional.empty();
    }
}
