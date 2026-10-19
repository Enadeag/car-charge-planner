package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.page.Card;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Set-up's step before the last: one forecast from the primary provider for the system as entered, so the owner knows
 * it works before Finish. A failure only says so; the app keeps trying once it is set up.
 */
final class SetUpCheck implements SetUpPage {

    private final BiFunction<EngineConfig, Secrets, ConnectionResult> check;
    private final List<ProviderInfo> providers;
    private final Background background;
    private final Messages messages;
    private final Label status = FieldRow.paragraph("", "status-text");
    private final Button again;
    private final PageView view;
    private EngineConfig config;
    private Secrets keys = Secrets.NONE;
    private int asked;

    /**
     * Makes the step; nothing is asked until {@link #run(EngineConfig, Secrets)}.
     *
     * @param check asks the config's primary provider for one forecast with these keys, nothing saved; blocks, so it
     *     is called away from the JavaFX thread
     * @param providers what the known providers tell about themselves, to name the one asked
     * @param background asks the provider away from the JavaFX thread
     * @param messages the texts, in the language set-up speaks
     */
    SetUpCheck(BiFunction<EngineConfig, Secrets, ConnectionResult> check, List<ProviderInfo> providers,
            Background background, Messages messages) {
        this.check = check;
        this.providers = List.copyOf(providers);
        this.background = background;
        this.messages = messages;
        again = new Button(messages.text(TextKey.SETUP_CHECK_AGAIN));
        again.setOnAction(_ -> ask());
        var buttons = new HBox(again);
        buttons.getStyleClass().add("card-buttons");
        view = new PageView(messages.text(SetUpStep.CHECK.title()));
        view.show(new Card(messages.text(TextKey.SETUP_CHECK_CARD), messages.text(TextKey.SETUP_CHECK_HINT), status,
                buttons));
    }

    /**
     * Asks the primary provider of a config, in the background.
     *
     * @param entered the whole config as entered
     * @param enteredKeys every provider's secret settings as entered
     */
    void run(EngineConfig entered, Secrets enteredKeys) {
        config = entered;
        keys = enteredKeys;
        ask();
    }

    @Override
    public PageView view() {
        return view;
    }

    @Override
    public void enter(SetUpForm form) {
        run(form.config(), form.keys());
    }

    /** Only the latest answer counts: the owner may ask again, or come back with other values, before one arrives. */
    private void ask() {
        var name = name();
        var asking = ++asked;
        var askedConfig = config;
        var askedKeys = keys;
        again.setDisable(true);
        status.getStyleClass().remove("problem");
        status.setText(messages.text(TextKey.SETUP_CHECK_ASKING, name));
        Consumer<ConnectionResult> answered = result -> {
            if (asking == asked) {
                show(name, result);
            }
        };
        background.run("checking the forecast for set-up", () -> check.apply(askedConfig, askedKeys), answered,
                failure -> answered.accept(new ConnectionResult(false, 0, Background.describe(failure, messages))));
    }

    private void show(String name, ConnectionResult result) {
        again.setDisable(false);
        if (result.worked()) {
            status.setText(messages.text(TextKey.SETUP_CHECK_WORKS, name, result.days()));
        } else {
            // the engine's words end with a full stop or without one
            status.setText(messages.text(TextKey.SETUP_CHECK_FAILED, result.message().replaceFirst("\\.$", "")));
            status.getStyleClass().add("problem");
        }
    }

    private String name() {
        return new ChoiceNames(messages).provider(config.forecast().primary(), providers);
    }
}
