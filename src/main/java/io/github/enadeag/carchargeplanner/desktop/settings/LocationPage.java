package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.forecast.Place;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.layout.Region;

/** Where the home is: found by name through the place search, or typed as coordinates, with its time zone. */
final class LocationPage implements ConfigPage {

    private static final NumberField.Range LATITUDE = new NumberField.Range(-90, 90, 0.01, 4);
    private static final NumberField.Range LONGITUDE = new NumberField.Range(-180, 180, 0.01, 4);

    private final Messages messages;
    private final PageView view;
    private final PlaceSearchCard search;
    private final NumberField latitude;
    private final NumberField longitude;
    private final ChoiceField<ZoneId> timezone;
    private final ProblemRows problems;
    private final Card coordinates;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param lookup the place search, through the engine
     * @param messages the texts, in the owner's language
     */
    LocationPage(PlaceLookup lookup, Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.LOCATION.title()));
        this.search = new PlaceSearchCard(lookup, this::choose, messages);
        latitude = new NumberField(LATITUDE, "°", messages);
        longitude = new NumberField(LONGITUDE, "°", messages);
        timezone = new ChoiceField<>(ZoneId.getAvailableZoneIds().stream().sorted().map(ZoneId::of).toList(),
                ZoneId::getId);
        problems = new ProblemRows(messages);
        coordinates = new Card(messages.text(TextKey.CARD_COORDINATES), messages.text(TextKey.CARD_COORDINATES_HINT),
                problems.add(Setting.LATITUDE,
                        FieldRow.of(messages, TextKey.FIELD_LATITUDE, TextKey.FIELD_LATITUDE_HINT, latitude)),
                problems.add(Setting.LONGITUDE,
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
        view.show(Unavailable.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        problems.clear();
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var place = new Coordinates(latitude.value(), longitude.value());
            return Optional.of(config.withSystem(config.system().withLocation(place, timezone.value()),
                    config.typical()));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    private void choose(Place place) {
        latitude.set(place.location().latitude());
        longitude.set(place.location().longitude());
        timezone.set(place.timezone());
    }

    private List<Input> inputs() {
        return Stream.<Input>of(latitude, longitude, timezone).toList();
    }
}
