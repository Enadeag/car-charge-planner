package nl.enadeag.carchargeplanner.desktop.settings.solar;

import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * The panel arrays, one card each: panels that face another way or have another tilt, such as an east and a west roof,
 * are an array of their own. The forecast is the sum of all of them. An added array starts empty; so does, in set-up,
 * an array whose size is a typical value, such as the one of set-up's draft.
 */
public final class SolarPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final List<PanelArrayCard> cards = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final HBox add;
    private final Supplier<Year> thisYear;
    private final boolean setUp;
    private int loadedCount;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param thisYear the current year, which a year placed may not be after; asked whenever the arrays are read
     * @param setUp true in set-up, which shows an array whose size is a typical value as a new, empty one
     * @param messages the texts, in the owner's language
     */
    public SolarPage(Supplier<Year> thisYear, boolean setUp, Messages messages) {
        this.messages = messages;
        this.thisYear = thisYear;
        this.setUp = setUp;
        this.view = new PageView(messages.text(Category.SOLAR.title()));
        var button = new Button(messages.text(TextKey.PANELS_ADD));
        button.setOnAction(_ -> addArray());
        add = new HBox(button);
        add.getStyleClass().add("card-buttons");
    }

    @Override
    public Category category() {
        return Category.SOLAR;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        listeners.add(action);
        cards.forEach(card -> card.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return cards.size() != loadedCount || cards.stream().anyMatch(PanelArrayCard::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        cards.clear();
        var panels = config.system().panels();
        for (int index = 0; index < panels.size(); index++) {
            var typical = config.typical().ofPanel(index);
            if (setUp && typical.contains(ConfigSetting.PANEL_SIZE)) {
                newCard().loadNew();
            } else {
                newCard().load(panels.get(index), typical);
            }
        }
        loadedCount = panels.size();
        showCards();
    }

    @Override
    public void showUnavailable(String reason) {
        cards.clear();
        loadedCount = 0;
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        var panels = new ArrayList<PanelArray>();
        for (var card : cards) {
            card.read().ifPresent(panels::add);
        }
        if (panels.size() < cards.size()) {
            return Optional.empty();
        }
        if (!isChanged()) {
            return Optional.of(config);
        }
        var typical = new TypicalValues(config.typical().system(), cards.stream().map(PanelArrayCard::typical).toList());
        return Optional.of(config.withSystem(config.system().withPanels(panels), typical));
    }

    /** Every card shows its own problems, also after one that is refused. */
    @Override
    public boolean showProblems() {
        return cards.stream().map(PanelArrayCard::showProblems).toList().contains(true);
    }

    /** One more array, empty but for the typical losses, tagged as such until the owner enters their own. */
    private void addArray() {
        newCard().loadNew();
        showCards();
        listeners.forEach(Runnable::run);
    }

    private PanelArrayCard newCard() {
        var card = new PanelArrayCard(this::remove, thisYear, messages);
        cards.add(card);
        listeners.forEach(card::onChange);
        return card;
    }

    /** The arrays after the removed one move up; what was typed in them stays. */
    private void remove(PanelArrayCard card) {
        cards.remove(card);
        showCards();
        listeners.forEach(Runnable::run);
    }

    private void showCards() {
        for (int index = 0; index < cards.size(); index++) {
            cards.get(index).placeAmong(index, cards.size());
        }
        var nodes = new ArrayList<Node>(cards.stream().map(PanelArrayCard::view).toList());
        nodes.add(add);
        view.show(nodes.toArray(Node[]::new));
    }
}
