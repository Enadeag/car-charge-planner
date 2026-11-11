package nl.enadeag.carchargeplanner.desktop.settings.location;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChosenPlace;
import nl.enadeag.carchargeplanner.desktop.settings.form.LocationEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberText;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.Place;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Where the home is: at the top the place chosen, or that none is yet; below it the place search; and the coordinates
 * and the time zone folded under "Coordinates and time zone", closed with a place chosen and open without one. A
 * problem in a coordinate opens the fold and keeps it open until the problem goes. When the primary provider has no
 * place search the page shows only the coordinates and the time zone, unfolded.
 */
public final class LocationPage implements ConfigPage {

    private static final NumberRange LATITUDE = new NumberRange(-90, 90, 0.01, ChosenPlace.DECIMALS);
    private static final NumberRange LONGITUDE = new NumberRange(-180, 180, 0.01, ChosenPlace.DECIMALS);

    private final Messages messages;
    private final NumberText numbers;
    private final Predicate<ProviderId> hasSearch;
    private final PageView view;
    private final PlaceSearchCard search;
    private final NumberInput latitude;
    private final NumberInput longitude;
    private final ChoiceInput<ZoneId> timezone;
    private final ProblemRows problems;
    private final Label chosenLine = Labels.paragraph("", "card-title");
    private final VBox chosenCard;
    private final Label noSearchTitle;
    private final Label noSearchNote;
    private final Fold fold;
    private final VBox coordinates;
    private final FieldRow latitudeRow;
    private final List<Runnable> changeActions = new ArrayList<>();
    /** Null while no place is chosen. */
    private ChosenPlace chosen;
    /** Null when the settings hold no place name. */
    private PlaceName loadedName;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param searcher the place search, through the engine
     * @param hasSearch whether a provider has a place search, asked as the page loads, before anything is searched
     * @param background asks the place search away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    public LocationPage(PlaceSearcher searcher, Predicate<ProviderId> hasSearch, Background background,
            Messages messages) {
        this.messages = messages;
        this.numbers = new NumberText(messages);
        this.hasSearch = hasSearch;
        this.view = new PageView(messages.text(Category.LOCATION.title()));
        this.search = new PlaceSearchCard(searcher, this::choose, background, messages);
        latitude = new NumberInput(LATITUDE, "°", messages);
        longitude = new NumberInput(LONGITUDE, "°", messages);
        timezone = new ChoiceInput<>(ZoneId.getAvailableZoneIds().stream().sorted().map(ZoneId::of).toList(),
                ZoneId::getId);
        problems = new ProblemRows(messages);
        latitudeRow = problems.add(ConfigSetting.LATITUDE,
                FieldRow.of(messages, TextKey.FIELD_LATITUDE, TextKey.FIELD_LATITUDE_HINT, latitude));
        chosenCard = new VBox(chosenLine);
        chosenCard.getStyleClass().addAll("card", "settings-card");
        noSearchTitle = Labels.paragraph(messages.text(TextKey.CARD_COORDINATES), "card-title");
        noSearchNote = Labels.paragraph(messages.text(TextKey.PLACE_NO_SEARCH), "note");
        List.of(noSearchTitle, noSearchNote).forEach(label -> label.managedProperty().bind(label.visibleProperty()));
        fold = new Fold(messages.text(TextKey.CARD_COORDINATES),
                Labels.paragraph(messages.text(TextKey.CARD_COORDINATES_HINT), "note"), latitudeRow,
                problems.add(ConfigSetting.LONGITUDE,
                        FieldRow.of(messages, TextKey.FIELD_LONGITUDE, TextKey.FIELD_LONGITUDE_HINT, longitude)),
                FieldRow.of(messages, TextKey.FIELD_TIMEZONE, TextKey.FIELD_TIMEZONE_HINT, timezone));
        coordinates = new VBox(noSearchTitle, noSearchNote, fold);
        coordinates.getStyleClass().addAll("card", "settings-card");
        // the line follows every keystroke: a real difference from the chosen place's coordinates drops its name
        inputs().forEach(input -> input.onChange(this::showPlace));
    }

    @Override
    public Category category() {
        return Category.LOCATION;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
        changeActions.add(action);
    }

    @Override
    public boolean isChanged() {
        return inputs().stream().anyMatch(Input::isChanged)
                || !Objects.equals(placeName().orElse(null), loadedName);
    }

    @Override
    public void load(EngineConfig config) {
        var system = config.system();
        loadedName = system.place().orElse(null);
        chosen = system.place().map(name -> new ChosenPlace(name, system.location())).orElse(null);
        latitude.load(system.location().latitude());
        longitude.load(system.location().longitude());
        timezone.load(system.timezone());
        var primary = config.forecast().primary();
        search.searchWith(primary);
        problems.clear();
        fold.holdOpen(false);
        showPlace();
        if (hasSearch.test(primary)) {
            noSearchTitle.setVisible(false);
            noSearchNote.setVisible(false);
            fold.showWithoutRow(false);
            fold.setOpen(placeName().isEmpty());
            view.show(chosenCard, search.view(), coordinates);
        } else {
            noSearchTitle.setVisible(true);
            noSearchNote.setVisible(true);
            fold.showWithoutRow(true);
            view.show(coordinates);
        }
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            return Optional.of(entry().applyTo(config));
        } catch (InvalidSetting _) {
            return Optional.empty();
        }
    }

    @Override
    public boolean showProblems() {
        problems.clear();
        fold.holdOpen(false);
        if (!isChanged()) {
            return false;
        }
        inputs().forEach(Input::commitTyped);
        try {
            entry().coordinates();
            return false;
        } catch (InvalidSetting e) {
            problems.show(e);
            fold.holdOpen(true);
            return true;
        }
    }

    /**
     * Says at the latitude that no place is chosen yet, as set-up needs one before it goes on, and opens the
     * coordinates to show it. What the owner typed but did not confirm is confirmed first.
     *
     * @return true when no place is chosen, the coordinates still being {@link Coordinates#NOT_CHOSEN}; false when one
     *     is, and nothing is shown
     */
    public boolean markNotChosen() {
        inputs().forEach(Input::commitTyped);
        if (entry().isChosen()) {
            return false;
        }
        latitudeRow.showProblem(messages.text(TextKey.PROBLEM_NO_PLACE));
        fold.holdOpen(true);
        return true;
    }

    private void choose(Place place, String label) {
        chosen = PlaceName.fromLabel(label).map(name -> new ChosenPlace(name, place.location())).orElse(null);
        latitude.set(place.location().latitude());
        longitude.set(place.location().longitude());
        timezone.set(place.timezone());
        problems.clear();
        fold.holdOpen(false);
        fold.setOpen(placeName().isEmpty());
        showPlace();
        // a place at the coordinates already shown changes no field, but its name is a change to save
        changeActions.forEach(Runnable::run);
    }

    private Optional<PlaceName> placeName() {
        return chosen == null ? Optional.empty() : chosen.nameAt(latitude.value(), longitude.value(), numbers);
    }

    private void showPlace() {
        var name = placeName();
        chosenLine.setText(name.map(place -> messages.text(TextKey.PLACE_CHOSEN, place.text()))
                .orElseGet(() -> messages.text(TextKey.PLACE_NOT_CHOSEN)));
        search.showChosen(name.isPresent());
    }

    private LocationEntry entry() {
        return new LocationEntry(latitude.value(), longitude.value(), timezone.value(), placeName());
    }

    private List<Input> inputs() {
        return Stream.<Input>of(latitude, longitude, timezone).toList();
    }
}
