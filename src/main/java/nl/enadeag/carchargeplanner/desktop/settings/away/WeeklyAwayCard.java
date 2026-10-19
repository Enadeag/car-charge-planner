package nl.enadeag.carchargeplanner.desktop.settings.away;

import nl.enadeag.carchargeplanner.desktop.settings.page.Card;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Texts;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.WeeklyAwaySchedule;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The weekly away periods: times each week when the car is not at home, so no charging is planned then. One period
 * covers one weekday; several days take several periods.
 */
public final class WeeklyAwayCard implements ConfigPart {

    private static final WeeklyAwayPeriod NEW = new WeeklyAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0),
            LocalTime.of(17, 0));

    private final Messages messages;
    private final VBox rows = new VBox();
    private final Label empty;
    private final Card card;
    private final List<AwayRow> shown = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private List<WeeklyAwayPeriod> loaded = List.of();

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public WeeklyAwayCard(Messages messages) {
        this.messages = messages;
        empty = Texts.paragraph(messages.text(TextKey.AWAY_NONE), "note");
        rows.getStyleClass().add("away-rows");
        var add = new Button(messages.text(TextKey.AWAY_ADD));
        add.setOnAction(_ -> {
            addRow(NEW);
            listeners.forEach(Runnable::run);
        });
        var buttons = new HBox(add);
        buttons.getStyleClass().add("card-buttons");
        card = new Card(messages.text(TextKey.CARD_AWAY), messages.text(TextKey.CARD_AWAY_HINT), empty, rows, buttons);
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    public Card view() {
        return card;
    }

    @Override
    public void onChange(Runnable action) {
        listeners.add(action);
        shown.forEach(row -> row.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return shown.size() != loaded.size() || shown.stream().anyMatch(AwayRow::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        loaded = config.away().weekly();
        shown.clear();
        rows.getChildren().clear();
        loaded.forEach(this::addRow);
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        var periods = new ArrayList<WeeklyAwayPeriod>();
        shown.forEach(row -> row.value().ifPresent(periods::add));
        if (periods.size() < shown.size()) {
            return Optional.empty();
        }
        return Optional.of(isChanged() ? config.withAway(new WeeklyAwaySchedule(periods)) : config);
    }

    private void addRow(WeeklyAwayPeriod period) {
        var row = new AwayRow(period, this::remove, messages);
        listeners.forEach(row::onChange);
        shown.add(row);
        rows.getChildren().add(row);
        showEmpty();
    }

    private void numberRows() {
        for (int i = 0; i < shown.size(); i++) {
            shown.get(i).number(i, shown.size());
        }
    }

    private void remove(AwayRow row) {
        shown.remove(row);
        rows.getChildren().remove(row);
        showEmpty();
        listeners.forEach(Runnable::run);
    }

    private void showEmpty() {
        numberRows();
        empty.setVisible(shown.isEmpty());
        empty.setManaged(shown.isEmpty());
    }
}
