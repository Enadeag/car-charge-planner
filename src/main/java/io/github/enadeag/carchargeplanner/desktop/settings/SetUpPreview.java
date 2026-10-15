package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.DashboardPreview;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.function.BiFunction;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.StackPane;

/** Set-up's last step: the owner's dashboard, planned with what they entered, before Finish saves it. */
final class SetUpPreview implements SetUpPage {

    private final BiFunction<EngineConfig, Secrets, Outlook> preview;
    private final DashboardPreview dashboard;
    private final PageView view;
    private int asked;

    /**
     * Makes the step; nothing is planned until {@link #run(EngineConfig, Secrets)}.
     *
     * @param preview plans a config with these keys, nothing saved and nobody told; blocks while the forecast is
     *     fetched, so it is called away from the JavaFX thread
     * @param reducedMotion true while the system asks for reduced motion; the charts then change without moving
     * @param messages the texts, in the language set-up speaks
     */
    SetUpPreview(BiFunction<EngineConfig, Secrets, Outlook> preview, ObservableValue<Boolean> reducedMotion,
            Messages messages) {
        this.preview = preview;
        this.dashboard = new DashboardPreview(reducedMotion, messages);
        var frame = new StackPane(dashboard.view());
        frame.getStyleClass().add("preview-frame");
        view = new PageView(messages.text(SetUpStep.PREVIEW.title()));
        view.show(FieldRow.paragraph(messages.text(TextKey.SETUP_PREVIEW_HINT), "field-label"), frame);
    }

    /**
     * Plans a config in the background and shows the dashboard with it; until then, that the forecast is fetched.
     *
     * @param config the whole config as entered
     * @param keys every provider's secret settings as entered
     */
    void run(EngineConfig config, Secrets keys) {
        var asking = ++asked;
        dashboard.showLoading();
        Thread.ofVirtual().start(() -> {
            var outlook = preview.apply(config, keys);
            Platform.runLater(() -> {
                if (asking == asked) {
                    dashboard.show(outlook);
                }
            });
        });
    }

    @Override
    public PageView view() {
        return view;
    }

    @Override
    public void enter(SetUpForm form) {
        run(form.config(), form.keys());
    }
}
