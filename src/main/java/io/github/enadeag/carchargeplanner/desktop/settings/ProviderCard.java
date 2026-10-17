package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * One forecast provider: what it is, what it is sent, its limits and terms, the settings it asks for (secret ones
 * hidden), and a test that asks it for a forecast with what is entered.
 */
final class ProviderCard {

    private final ProviderInfo info;
    private final Background background;
    private final Messages messages;
    private final Map<String, Input> inputs = new LinkedHashMap<>();
    private final Map<String, FieldRow> rows = new LinkedHashMap<>();
    private final Label result = FieldRow.paragraph("", "note");
    private final Button test;
    private final Card card;

    /**
     * Makes the card, with empty settings until {@link #load(Map, Map, boolean)}.
     *
     * @param info what the provider tells about itself
     * @param tester asks the provider for a forecast; called away from the JavaFX thread
     * @param openLink opens a web page in the owner's browser
     * @param background asks the provider away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    ProviderCard(ProviderInfo info, ConnectionTest tester, Consumer<URI> openLink, Background background,
            Messages messages) {
        this.info = info;
        this.background = background;
        this.messages = messages;
        var texts = new ProviderTexts(messages);
        var terms = new Hyperlink(messages.text(TextKey.PROVIDER_TERMS));
        terms.setOnAction(event -> openLink.accept(URI.create(info.termsUrl())));
        // the fallback provider's card can show below the first one's: a screen reader names whose each control is
        terms.setAccessibleText(messages.text(TextKey.IN_GROUP, terms.getText(), info.name()));
        var content = new ArrayList<Node>(List.of(
                FieldRow.paragraph(messages.text(TextKey.PROVIDER_SENDS, texts.sends(info)), "note"),
                FieldRow.paragraph(messages.text(TextKey.PROVIDER_LIMITS, info.minRefresh().toMinutes(),
                        Math.max(1, info.horizon().toDays())), "note"),
                terms));
        for (var setting : info.settings()) {
            Input input = setting.secret() ? new SecretInput(messages) : new TextInput();
            var row = new FieldRow(texts.label(setting), texts.hint(info, setting), input);
            row.setSpokenName(messages.text(TextKey.IN_GROUP, row.name(), info.name()));
            if (!setting.optional()) {
                row.showTag(messages.text(TextKey.PROVIDER_REQUIRED));
            }
            inputs.put(setting.key(), input);
            rows.put(setting.key(), row);
            content.add(row);
        }
        test = new Button(messages.text(TextKey.PROVIDER_TEST));
        test.setOnAction(event -> test(tester));
        test.setAccessibleText(messages.text(TextKey.IN_GROUP, test.getText(), info.name()));
        var line = new HBox(test, result);
        line.getStyleClass().add("field-line");
        content.add(line);
        card = new Card(info.name(), texts.description(info), content.toArray(Node[]::new));
    }

    /**
     * Shows the stored settings, as the ones not edited.
     *
     * @param plain the provider's settings from the config file
     * @param secret the provider's secret settings from the keychain
     * @param keysLocked true when the stored secret settings could not be read as the keyring is locked: a secret
     *     field without a value then shows as locked, so it never empties a stored one
     */
    void load(Map<String, String> plain, Map<String, String> secret, boolean keysLocked) {
        for (var setting : info.settings()) {
            var stored = (setting.secret() ? secret : plain).getOrDefault(setting.key(), "");
            switch (inputs.get(setting.key())) {
                case SecretInput field when keysLocked && stored.isEmpty() -> field.lock();
                case SecretInput field -> field.load(stored);
                case TextInput field -> field.load(stored);
                default -> throw new IllegalStateException("a provider setting is a text or a secret");
            }
        }
        rows.values().forEach(FieldRow::clearProblem);
        result.setText("");
    }

    /**
     * The settings that are not secret, as entered.
     *
     * @return per setting key its value; empty ones left out
     */
    Map<String, String> plain() {
        return values(false);
    }

    /**
     * The secret settings, as entered.
     *
     * @return per setting key its value; empty ones left out
     */
    Map<String, String> secret() {
        return values(true);
    }

    /**
     * Marks the required settings that are empty and the settings whose value has the wrong form, such as a server
     * address a key may not be sent to; problems shown before are taken away first. A locked secret counts as there:
     * it is stored, only not readable now.
     *
     * @return true when every required setting has a value and every value has the right form
     */
    boolean check() {
        rows.values().forEach(FieldRow::clearProblem);
        var missing = new ArrayList<>(info.missingRequired(info.settingsFrom(plain(), secret())));
        info.settings().stream().filter(setting -> inputs.get(setting.key()) instanceof SecretInput field
                && field.isLocked()).forEach(setting -> missing.remove(setting.label()));
        info.settings().stream().filter(setting -> missing.contains(setting.label()))
                .forEach(setting -> rows.get(setting.key()).showProblem(messages.text(TextKey.PROBLEM_REQUIRED)));
        var entered = info.settingsFrom(plain(), secret());
        var wrong = info.settings().stream()
                .filter(setting -> !setting.format().accepts(entered.getOrDefault(setting.key(), "")))
                .toList();
        wrong.forEach(setting -> rows.get(setting.key()).showProblem(messages.text(problem(setting.format()))));
        return missing.isEmpty() && wrong.isEmpty();
    }

    private static TextKey problem(SettingFormat format) {
        return switch (format) {
            case SERVER_ADDRESS -> TextKey.PROBLEM_SERVER_ADDRESS;
            case LETTERS_AND_DIGITS -> TextKey.PROBLEM_LETTERS_AND_DIGITS;
            case TEXT -> throw new IllegalArgumentException("any text has the right form");
        };
    }

    /**
     * Whether the owner changed a setting since they were loaded.
     *
     * @return true when a setting differs from the stored one
     */
    boolean isChanged() {
        return inputs.values().stream().anyMatch(Input::isChanged);
    }

    /**
     * Calls an action whenever a setting changes.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action) {
        inputs.values().forEach(input -> input.onChange(action));
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    Card view() {
        return card;
    }

    private Map<String, String> values(boolean secret) {
        var values = new LinkedHashMap<String, String>();
        for (var setting : info.settings()) {
            if (setting.secret() == secret) {
                var value = switch (inputs.get(setting.key())) {
                    case SecretInput field -> field.value();
                    case TextInput field -> field.value();
                    default -> "";
                };
                if (!value.isEmpty()) {
                    values.put(setting.key(), value);
                }
            }
        }
        return values;
    }

    private void test(ConnectionTest tester) {
        var settings = new LinkedHashMap<>(plain());
        settings.putAll(secret());
        test.setDisable(true);
        result.setText(messages.text(TextKey.PROVIDER_TESTING, info.name()));
        background.run("testing a forecast provider", () -> tester.attempt(info.id(), settings), this::showResult,
                failure -> showResult(new ConnectionResult(false, 0, Background.describe(failure, messages))));
    }

    private void showResult(ConnectionResult outcome) {
        test.setDisable(false);
        result.setText(outcome.worked() ? messages.text(TextKey.PROVIDER_WORKS, outcome.days())
                : messages.text(TextKey.PROVIDER_FAILED, outcome.message()));
    }
}
