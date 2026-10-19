package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.dashboard.DashboardPreview;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.StackPane;

/** Set-up's last step: the owner's dashboard, planned with what they entered, before Finish saves it. */
final class SetUpPreview implements SetUpStepPage {

    private final BiFunction<EngineConfig, Secrets, Outlook> preview;
    private final Background background;
    private final Messages messages;
    private final DashboardPreview dashboard;
    private final PageView view;
    private int asked;

    /**
     * Makes the step; nothing is planned until {@link #plan(EngineConfig, Secrets)}.
     *
     * @param preview plans a config with these keys, nothing saved and nobody told; blocks while the forecast is
     *     fetched, so it is called away from the JavaFX thread
     * @param reducedMotion true while the system asks for reduced motion; the charts then change without moving
     * @param appName the app's name, for the dashboard's header
     * @param background plans away from the JavaFX thread
     * @param messages the texts, in the language set-up speaks
     */
    SetUpPreview(BiFunction<EngineConfig, Secrets, Outlook> preview, ObservableValue<Boolean> reducedMotion,
            String appName, Background background, Messages messages) {
        this.preview = preview;
        this.background = background;
        this.messages = messages;
        this.dashboard = new DashboardPreview(reducedMotion, appName, messages);
        var frame = new StackPane(dashboard.view());
        frame.getStyleClass().add("preview-frame");
        view = new PageView(messages.text(SetUpStep.PREVIEW.title()));
        view.show(Labels.paragraph(messages.text(TextKey.SET_UP_PREVIEW_HINT), "field-label"), frame);
    }

    /**
     * Plans a config in the background and shows the dashboard with it; until then, that the forecast is fetched.
     *
     * @param config the whole config as entered
     * @param keys every provider's secret settings as entered
     */
    void plan(EngineConfig config, Secrets keys) {
        var asking = ++asked;
        dashboard.showLoading();
        Consumer<Outlook> planned = outlook -> {
            if (asking == asked) {
                dashboard.show(outlook);
            }
        };
        background.run("planning the preview for set-up", () -> preview.apply(config, keys), planned,
                failure -> planned.accept(new Outlook.NoForecast(Background.describe(failure, messages))));
    }

    @Override
    public PageView view() {
        return view;
    }

    @Override
    public void onReached(SetUpForm form) {
        plan(form.config(), form.keys());
    }
}
