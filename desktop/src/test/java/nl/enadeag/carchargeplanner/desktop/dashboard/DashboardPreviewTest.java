package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.host.AppName;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@FxTest
class DashboardPreviewTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");

    private Stage stage;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private Node shown(Outlook outlook) throws Exception {
        return Fx.call(() -> {
            var preview = new DashboardPreview(Fx.REDUCED_MOTION, AppName.TITLE, new Messages(Language.ENGLISH));
            preview.show(outlook);
            stage = Scenes.show(preview.view(), ColorScheme.LIGHT);
            return preview.view();
        });
    }

    @Test
    void thePreviewOfAPlanLeavesOutTheWarningThatAKeyWasRefusedAsSettingsIsNotYetThere() throws Exception {
        var source = new ForecastSource(new ProviderId("forecast-solar"), "Forecast.Solar", "Solar forecast",
                PlannedDays.TWO_DAYS.madeAt(), Freshness.FRESH, ProviderRole.FALLBACK, "Open-Meteo", Optional.empty(),
                List.of(OPEN_METEO));
        var planned = new Outlook.Planned(PlannedDays.TWO_DAYS.madeAt(), PlannedDays.BRUSSELS,
                PlannedDays.TWO_DAYS.days(), PlannedDays.TWO_DAYS.batteryLevelPct(), false, Optional.empty(), List.of(),
                source, 0.4, PlannedDays.TWO_DAYS.minimumPct(), PlannedDays.TWO_DAYS.targetPct());

        var view = shown(planned);

        assertThat(Fx.call(() -> view.lookup(".main-card"))).isNotNull();
        assertThat(Fx.call(() -> view.lookup(".warning-card"))).isNull();
        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).noneMatch(text -> text.contains("refused the API key"));
    }

    @Test
    void thePreviewWithoutAForecastLeavesOutTheWarningToo() throws Exception {
        var view = shown(new Outlook.NoForecast("Open-Meteo refused the request (status 401)", false,
                List.of(OPEN_METEO)));

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("No forecast")
                .noneMatch(text -> text.contains("refused the API key"));
        assertThat(Fx.call(() -> view.lookup(".warning-card"))).isNull();
    }
}
