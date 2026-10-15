package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Units;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;

/**
 * The desktop app's own settings: how it shows itself, its language and appearance, the units, and whether it starts
 * at login. The first four are kept in the app settings file, start at login by the system.
 */
final class AppPage implements Page {

    private final Messages messages;
    private final PageView view;
    private final ChoiceField<DisplayMode> mode;
    private final ChoiceField<Optional<Language>> language;
    private final ChoiceField<Appearance> appearance;
    private final ChoiceField<Units> units;
    private final SwitchField startAtLogin;
    private final FieldRow startAtLoginRow;
    private final Card[] cards;

    /**
     * Makes the page, showing the first choices until {@link #load(AppSettings, Optional)}.
     *
     * @param messages the texts, in the owner's language
     */
    AppPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.APP.title()));
        mode = new ChoiceField<>(List.of(DisplayMode.values()), this::modeName);
        language = new ChoiceField<>(languages(), this::languageName);
        appearance = new ChoiceField<>(List.of(Appearance.values()), this::appearanceName);
        units = new ChoiceField<>(List.of(Units.values()), this::unitsName);
        startAtLogin = new SwitchField(messages.text(TextKey.FIELD_START_AT_LOGIN));
        startAtLoginRow = new FieldRow("", "", startAtLogin);
        cards = new Card[] {
                new Card(messages.text(TextKey.CARD_DISPLAY), messages.text(TextKey.CARD_DISPLAY_HINT),
                        new FieldRow(messages.text(TextKey.FIELD_MODE), messages.text(TextKey.FIELD_MODE_HINT), mode),
                        new FieldRow(messages.text(TextKey.FIELD_LANGUAGE), messages.text(TextKey.FIELD_LANGUAGE_HINT),
                                language),
                        new FieldRow(messages.text(TextKey.FIELD_APPEARANCE), "", appearance)),
                new Card(messages.text(TextKey.CARD_UNITS), messages.text(TextKey.CARD_UNITS_HINT),
                        new FieldRow(messages.text(TextKey.FIELD_UNITS), "", units)),
                new Card(messages.text(TextKey.CARD_START_AT_LOGIN), messages.text(TextKey.CARD_START_AT_LOGIN_HINT),
                        startAtLoginRow)};
        view.show(cards);
    }

    /**
     * Shows the stored settings, as the ones not edited.
     *
     * @param settings the settings in the app settings file
     * @param startsAtLogin whether the system starts the app at login; empty when this copy of the app cannot start
     *     at login, such as one run from the build, and the box is then switched off with a note
     */
    void load(AppSettings settings, Optional<Boolean> startsAtLogin) {
        mode.load(settings.mode());
        language.load(settings.language());
        appearance.load(settings.appearance());
        units.load(settings.units());
        startAtLogin.load(startsAtLogin.orElse(false));
        startAtLogin.setEnabled(startsAtLogin.isPresent());
        startAtLoginRow.clearProblem();
        startAtLoginRow.setHint(startsAtLogin.isPresent() ? "" : messages.text(TextKey.START_AT_LOGIN_UNAVAILABLE));
    }

    /**
     * The settings as the page shows them now.
     *
     * @return the app settings to keep
     */
    AppSettings settings() {
        return new AppSettings(language.value(), units.value(), mode.value(), appearance.value());
    }

    /**
     * Offers to run set-up again, on a card of its own below the others.
     *
     * @param runSetUp closes Settings, asking first when there are unsaved changes, and opens set-up
     */
    void offerSetUp(Runnable runSetUp) {
        var button = new Button(messages.text(TextKey.RUN_SETUP));
        button.setOnAction(event -> runSetUp.run());
        var all = new ArrayList<Node>(List.of(cards));
        all.add(new Card(messages.text(TextKey.CARD_SETUP), messages.text(TextKey.CARD_SETUP_HINT), button));
        view.show(all.toArray(Node[]::new));
    }

    /**
     * Calls an action whenever the chosen units change, by the owner or by loading, so distances show in them at once.
     *
     * @param action gets the units chosen now, on the JavaFX thread
     */
    void onUnitsChanged(Consumer<Units> action) {
        units.onChange(() -> action.accept(units.value()));
    }

    /**
     * Whether the box asks for the app to start at login.
     *
     * @return true when ticked
     */
    boolean startsAtLogin() {
        return startAtLogin.value();
    }

    /**
     * Whether the owner changed the start at login box since it was loaded.
     *
     * @return true when the system has to be told
     */
    boolean startAtLoginChanged() {
        return startAtLogin.isChanged();
    }

    /**
     * Says that the system could not change start at login.
     *
     * @param detail the system's error, as it gave it
     */
    void showStartAtLoginFailure(String detail) {
        startAtLoginRow.showProblem(messages.text(TextKey.START_AT_LOGIN_FAILED, detail));
    }

    @Override
    public Category category() {
        return Category.APP;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        Stream.of(mode, language, appearance, units, startAtLogin).forEach(input -> input.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return Stream.of(mode, language, appearance, units, startAtLogin).anyMatch(Input::isChanged);
    }

    private static List<Optional<Language>> languages() {
        var choices = new ArrayList<Optional<Language>>();
        choices.add(Optional.empty());
        Stream.of(Language.values()).map(Optional::of).forEach(choices::add);
        return choices;
    }

    /** A language by its own name, such as "Nederlands", so that the owner finds it whatever the app speaks now. */
    private String languageName(Optional<Language> choice) {
        return choice.map(Language::autonym).orElseGet(() -> messages.text(TextKey.LANGUAGE_SYSTEM));
    }

    private String appearanceName(Appearance choice) {
        return messages.text(switch (choice) {
            case SYSTEM -> TextKey.APPEARANCE_SYSTEM;
            case LIGHT -> TextKey.APPEARANCE_LIGHT;
            case DARK -> TextKey.APPEARANCE_DARK;
        });
    }

    private String modeName(DisplayMode choice) {
        return messages.text(switch (choice) {
            case AUTOMATIC -> TextKey.MODE_AUTOMATIC;
            case TRAY -> TextKey.MODE_TRAY;
            case WINDOW -> TextKey.MODE_WINDOW;
        });
    }

    private String unitsName(Units choice) {
        return messages.text(switch (choice) {
            case METRIC -> TextKey.UNITS_METRIC;
            case IMPERIAL -> TextKey.UNITS_IMPERIAL;
        });
    }
}
