package nl.enadeag.carchargeplanner.desktop.settings.location;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.LocationEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.forecast.Place;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.layout.Region;

/** Where the home is: found by name through the place search, or typed as coordinates, with its time zone. */
public final class LocationPage implements ConfigPage {

    private static final NumberRange LATITUDE = new NumberRange(-90, 90, 0.01, 4);
    private static final NumberRange LONGITUDE = new NumberRange(-180, 180, 0.01, 4);

    private final Messages messages;
    private final PageView view;
    private final PlaceSearchCard search;
    private final NumberInput latitude;
    private final NumberInput longitude;
    private final ChoiceInput<ZoneId> timezone;
    private final ProblemRows problems;
    private final CardView coordinates;
    private final FieldRow latitudeRow;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param searcher the place search, through the engine
     * @param background asks the place search away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    public LocationPage(PlaceSearcher searcher, Background background, Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.LOCATION.title()));
        this.search = new PlaceSearchCard(searcher, this::choose, background, messages);
        latitude = new NumberInput(LATITUDE, "°", messages);
        longitude = new NumberInput(LONGITUDE, "°", messages);
        timezone = new ChoiceInput<>(ZoneId.getAvailableZoneIds().stream().sorted().map(ZoneId::of).toList(),
                ZoneId::getId);
        problems = new ProblemRows(messages);
        latitudeRow = problems.add(ConfigSetting.LATITUDE,
                FieldRow.of(messages, TextKey.FIELD_LATITUDE, TextKey.FIELD_LATITUDE_HINT, latitude));
        coordinates = new CardView(messages.text(TextKey.CARD_COORDINATES),
                messages.text(TextKey.CARD_COORDINATES_HINT), latitudeRow,
                problems.add(ConfigSetting.LONGITUDE,
                        FieldRow.of(messages, TextKey.FIELD_LONGITUDE, TextKey.FIELD_LONGITUDE_HINT, longitude)),
                FieldRow.of(messages, TextKey.FIELD_TIMEZONE, TextKey.FIELD_TIMEZONE_HINT, timezone));
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
    }

    @Override
    public boolean isChanged() {
        return inputs().stream().anyMatch(Input::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        var system = config.system();
        latitude.load(system.location().latitude());
        longitude.load(system.location().longitude());
        timezone.load(system.timezone());
        search.searchWith(config.forecast().primary());
        problems.clear();
        view.show(search.view(), coordinates);
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
        if (!isChanged()) {
            return false;
        }
        inputs().forEach(Input::commitTyped);
        try {
            entry().coordinates();
            return false;
        } catch (InvalidSetting e) {
            problems.show(e);
            return true;
        }
    }

    /**
     * Says at the latitude that no place is chosen yet, as set-up needs one before it goes on. What the owner typed
     * but did not confirm is confirmed first.
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
        return true;
    }

    private void choose(Place place) {
        latitude.set(place.location().latitude());
        longitude.set(place.location().longitude());
        timezone.set(place.timezone());
    }

    private LocationEntry entry() {
        return new LocationEntry(latitude.value(), longitude.value(), timezone.value());
    }

    private List<Input> inputs() {
        return Stream.<Input>of(latitude, longitude, timezone).toList();
    }
}
