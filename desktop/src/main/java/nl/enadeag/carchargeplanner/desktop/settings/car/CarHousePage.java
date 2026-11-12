package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.ArrayList;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The car, with typical values on request, and under "More settings" the car's trip surcharge and the house's own use,
 * with a typical value of its own. The fold is closed each time the page loads; its fields keep every value in
 * their ranges, so none has a problem that would open it. Distances and consumption show in the units the App page
 * chooses, also before they are saved.
 */
public final class CarHousePage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final CarCard car;
    private final HouseCard house;
    private final Fold more;
    private final VBox moreCard;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}, in metric units until {@link #showIn(Units)}.
     *
     * @param messages the texts, in the owner's language
     */
    public CarHousePage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.CAR_HOUSE.title()));
        car = new CarCard(messages, true);
        house = new HouseCard(messages);
        var folded = new ArrayList<>(car.folded());
        folded.addAll(house.rows());
        more = new Fold(messages.text(TextKey.MORE_SETTINGS), folded.toArray(Node[]::new));
        moreCard = new VBox(more);
        moreCard.getStyleClass().addAll("card", "settings-card");
    }

    /**
     * Shows distances and consumption in other units; what they stand for does not change.
     *
     * @param units the units to show them in
     */
    public void showIn(Units units) {
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
        more.setOpen(false);
        view.show(car.view(), moreCard);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        return car.read(config).flatMap(house::read);
    }

    /** Both cards are checked, so each shows its own problems. */
    @Override
    public boolean showProblems() {
        var carRefused = car.showProblems();
        var houseRefused = house.showProblems();
        return carRefused || houseRefused;
    }
}
