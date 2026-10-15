package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.AwaySchedule;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
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
final class AwayCard implements ConfigPart {

    private static final RecurringAwayPeriod NEW = new RecurringAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0),
            LocalTime.of(17, 0));

    private final Messages messages;
    private final VBox rows = new VBox();
    private final Label empty;
    private final Card card;
    private final List<AwayRow> shown = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private List<RecurringAwayPeriod> loaded = List.of();

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    AwayCard(Messages messages) {
        this.messages = messages;
        empty = FieldRow.paragraph(messages.text(TextKey.AWAY_NONE), "note");
        rows.getStyleClass().add("away-rows");
        var add = new Button(messages.text(TextKey.AWAY_ADD));
        add.setOnAction(event -> {
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
    Card view() {
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
        loaded = config.away().recurring();
        shown.clear();
        rows.getChildren().clear();
        loaded.forEach(this::addRow);
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        var periods = new ArrayList<RecurringAwayPeriod>();
        shown.forEach(row -> row.value().ifPresent(periods::add));
        if (periods.size() < shown.size()) {
            return Optional.empty();
        }
        return Optional.of(isChanged() ? config.withAway(new AwaySchedule(periods)) : config);
    }

    private void addRow(RecurringAwayPeriod period) {
        var row = new AwayRow(period, this::remove, messages);
        listeners.forEach(row::onChange);
        shown.add(row);
        rows.getChildren().add(row);
        showEmpty();
    }

    private void remove(AwayRow row) {
        shown.remove(row);
        rows.getChildren().remove(row);
        showEmpty();
        listeners.forEach(Runnable::run);
    }

    private void showEmpty() {
        empty.setVisible(shown.isEmpty());
        empty.setManaged(shown.isEmpty());
    }
}
