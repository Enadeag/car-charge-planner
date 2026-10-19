package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.DisplayMode;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
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
     * Makes the page as Settings shows it, showing the first choices until {@link #load(AppSettings, boolean)}.
     *
     * @param messages the texts, in the owner's language
     */
    AppPage(Messages messages) {
        this(messages, TextKey.CARD_DISPLAY_HINT);
    }

    /**
     * Makes the page, showing the first choices until {@link #load(AppSettings, boolean)}.
     *
     * @param messages the texts, in the owner's language
     * @param displayHint what the display card says about when a choice takes effect, which differs between
     *     Settings and set-up
     */
    AppPage(Messages messages, TextKey displayHint) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.APP.title()));
        var names = new ChoiceNames(messages);
        mode = new ChoiceField<>(List.of(DisplayMode.values()), names::mode);
        language = new ChoiceField<>(languages(), choice -> choice.map(names::language).orElseGet(names::systemLanguage));
        appearance = new ChoiceField<>(List.of(Appearance.values()), names::appearance);
        units = new ChoiceField<>(List.of(Units.values()), names::units);
        startAtLogin = new SwitchField(messages.text(TextKey.FIELD_START_AT_LOGIN));
        startAtLoginRow = new FieldRow("", "", startAtLogin);
        cards = new Card[] {
                new Card(messages.text(TextKey.CARD_DISPLAY), messages.text(displayHint),
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
     * @param startsAtLogin whether the system starts the app at login
     */
    void load(AppSettings settings, boolean startsAtLogin) {
        load(settings);
        startAtLogin.load(startsAtLogin);
        startAtLogin.setEnabled(true);
        startAtLoginRow.clearProblem();
        startAtLoginRow.setHint("");
    }

    /**
     * Shows the stored settings, as the ones not edited, for a copy of the app that cannot start at login, such as one
     * run from the build: the box is switched off, with a note.
     *
     * @param settings the settings in the app settings file
     */
    void loadWithoutStartAtLogin(AppSettings settings) {
        load(settings);
        startAtLogin.load(false);
        startAtLogin.setEnabled(false);
        startAtLoginRow.clearProblem();
        startAtLoginRow.setHint(messages.text(TextKey.START_AT_LOGIN_UNAVAILABLE));
    }

    private void load(AppSettings settings) {
        mode.load(settings.mode());
        language.load(settings.language());
        appearance.load(settings.appearance());
        units.load(settings.units());
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
        button.setOnAction(_ -> runSetUp.run());
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
     * Calls an action whenever the chosen language changes, by the owner or by loading, so set-up can speak it at
     * once.
     *
     * @param action gets the language chosen now, empty for the system's, on the JavaFX thread
     */
    void onLanguageChanged(Consumer<Optional<Language>> action) {
        language.onChange(() -> action.accept(language.value()));
    }

    /**
     * Calls an action whenever the chosen appearance changes, by the owner or by loading, so set-up can show it at
     * once.
     *
     * @param action gets the appearance chosen now, on the JavaFX thread
     */
    void onAppearanceChanged(Consumer<Appearance> action) {
        appearance.onChange(() -> action.accept(appearance.value()));
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
}
