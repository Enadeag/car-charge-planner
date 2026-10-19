package nl.enadeag.carchargeplanner.desktop.settings.location;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.page.Card;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.Place;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Finds the home's place from a name or postcode: the text goes to the primary forecast provider's place search, and
 * a chosen result fills in the coordinates and the time zone.
 */
final class PlaceSearchCard {

    private final PlaceLookup lookup;
    private final Consumer<Place> chosen;
    private final Background background;
    private final Messages messages;
    private final TextField text = new TextField();
    private final Button search;
    private final Label status = FieldRow.paragraph("", "note");
    private final VBox results = new VBox();
    private final Card card;
    private ProviderId provider = new ProviderId("open-meteo");

    /**
     * Makes the card.
     *
     * @param lookup asks the provider's place search; called away from the JavaFX thread
     * @param chosen gets the place the owner picked from the results, on the JavaFX thread
     * @param background asks the place search away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    PlaceSearchCard(PlaceLookup lookup, Consumer<Place> chosen, Background background, Messages messages) {
        this.lookup = lookup;
        this.chosen = chosen;
        this.background = background;
        this.messages = messages;
        text.setPromptText(messages.text(TextKey.PLACE_PROMPT));
        ScreenReader.name(text, messages.text(TextKey.PLACE_FIELD));
        text.setOnAction(event -> {
            event.consume(); // Enter here searches, it does not save Settings
            search();
        });
        search = new Button(messages.text(TextKey.PLACE_SEARCH));
        search.setOnAction(_ -> search());
        HBox.setHgrow(text, Priority.ALWAYS);
        var line = new HBox(text, search);
        line.getStyleClass().add("field-line");
        results.getStyleClass().add("place-results");
        // nothing to say or show yet: no empty space on the card either
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        results.managedProperty().bind(Bindings.isNotEmpty(results.getChildren()));
        card = new Card(messages.text(TextKey.CARD_PLACE), messages.text(TextKey.CARD_PLACE_HINT), line, status,
                results);
    }

    /**
     * Uses this provider's place search from now on, and forgets earlier results.
     *
     * @param primary the primary forecast provider as stored
     */
    void searchWith(ProviderId primary) {
        provider = primary;
        results.getChildren().clear();
        status.setText("");
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    Card view() {
        return card;
    }

    private void search() {
        var typed = text.getText().strip();
        var asked = provider;
        search.setDisable(true);
        results.getChildren().clear();
        status.setText(messages.text(TextKey.PLACE_SEARCHING));
        background.run("searching for a place", () -> lookup.search(asked, typed),
                found -> found.ifPresentOrElse(answer -> show(typed, answer), this::showNoSearch),
                failure -> show(typed, new PlaceResults.Failed(PlaceResults.Problem.UNAVAILABLE)));
    }

    /** The provider has no place search. */
    private void showNoSearch() {
        search.setDisable(false);
        status.setText(messages.text(TextKey.PLACE_NO_SEARCH));
    }

    private void show(String typed, PlaceResults found) {
        search.setDisable(false);
        switch (found) {
            case PlaceResults.Found places when places.places().isEmpty() ->
                    status.setText(messages.text(TextKey.PLACE_NONE, typed));
            case PlaceResults.Found places -> {
                status.setText(messages.text(TextKey.PLACE_CHOOSE));
                places.places().forEach(place -> results.getChildren().add(result(place, places.places())));
            }
            case PlaceResults.Failed failed -> status.setText(messages.text(switch (failed.problem()) {
                case TOO_SHORT -> TextKey.PLACE_TOO_SHORT;
                case UNAVAILABLE -> TextKey.PLACE_UNAVAILABLE;
                case RATE_LIMITED -> TextKey.PLACE_BUSY;
                case BAD_RESPONSE -> TextKey.PLACE_BAD_RESPONSE;
            }));
        }
    }

    /** A result as a button; two results that read alike name their finer area too. */
    private Button result(Place place, List<Place> all) {
        var alike = all.stream().filter(other -> other.label().equals(place.label())).count() > 1;
        var button = new Button(alike ? place.detailedLabel() : place.label());
        button.getStyleClass().addAll("flat", "place-result");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(_ -> {
            chosen.accept(place);
            results.getChildren().clear();
            status.setText(messages.text(TextKey.PLACE_CHOSEN, button.getText()));
        });
        return button;
    }
}
