package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.OutlookMapper;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import java.util.List;
import javafx.beans.value.ObservableValue;
import javafx.scene.layout.Region;

/**
 * The dashboard as it will look, for set-up to show before Finish: the same view, with the owner's own plan, but
 * without the buttons that lead elsewhere (open as a window, away periods, Settings, entering the battery level)
and without the warning about a refused API key, whose words point to Settings while the owner is still in set-up.
 *
 * <p>Make it and call it on the JavaFX thread.
 */
public final class DashboardPreview {

    private final OutlookMapper mapper;
    private final DashboardView view;

    /**
     * Makes the preview, saying that the forecast is being fetched until {@link #show(Outlook)}.
     *
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); the charts then change without moving; never
     *     null
     * @param appName the app's name, for the header; the same in every language
     * @param messages the texts, in the language set-up speaks
     */
    public DashboardPreview(ObservableValue<Boolean> reducedMotion, String appName, Messages messages) {
        // the preview never shows the warning, so it needs no provider's name
        this.mapper = new OutlookMapper(messages, List.of());
        this.view = new DashboardView(() -> { }, () -> { }, reducedMotion, appName, messages);
        view.setDetachable(false);
        view.hideFooterButtons();
        view.hideKeyRefused();
        view.getStyleClass().add("dashboard-preview");
        showLoading();
    }

    /**
     * The dashboard itself, to put on a page.
     *
     * @return the same node every time
     */
    public Region view() {
        return view;
    }

    /** Says that the forecast is being fetched, as the dashboard does before its first plan. */
    public void showLoading() {
        view.show(mapper.loading());
    }

    /**
     * Shows a plan, or why there is none, as the dashboard would.
     *
     * @param outlook the engine's preview of the owner's plan; never null
     */
    public void show(Outlook outlook) {
        view.show(mapper.map(outlook));
    }
}
