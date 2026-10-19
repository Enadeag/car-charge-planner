package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.scene.Node;

/**
 * Walks through every step of set-up as the owner sees it, with every value tagged as typical, two panel arrays, an
 * away period, the check and the preview answered, then a refused value and the question before leaving; and checks
 * each screen once it is laid out.
 */
final class SetUpScreens {

    private SetUpScreens() {
    }

    static List<String> problems(SetUpFixture setUp, SetUpView view, Function<Node, List<String>> check)
            throws Exception {
        var found = new ArrayList<String>();
        for (var step : SetUpStep.values()) {
            Fx.run(() -> {
                view.show(step);
                var tabs = view.lookup(".day-tabs"); // the preview's; a growing tab is narrow on purpose
                if (tabs != null) {
                    tabs.setStyle("-fx-open-tab-animation: none;");
                }
            });
            switch (step) {
                case SOLAR -> Fx.run(() -> setUp.button(view, TextKey.PANELS_ADD).fire());
                case MORE -> Fx.run(() -> setUp.button(view, TextKey.AWAY_ADD).fire());
                case CHECK -> SettingsFixture.waitFor(() -> !setUp.button(view, TextKey.SETUP_CHECK_AGAIN).isDisabled());
                case PREVIEW -> SettingsFixture.waitFor(() -> !SetUpFixture.texts(view)
                        .contains(setUp.messages.text(TextKey.LOADING)));
                default -> { }
            }
            found.addAll(Fx.call(() -> {
                SetUpFixture.texts(view);
                return check.apply(view);
            }));
        }
        found.addAll(Fx.call(() -> {
            view.show(SetUpStep.CHARGER);
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "10");
            setUp.type(view, TextKey.FIELD_MAX_CURRENT, "8");
            view.next();
            var refused = new ArrayList<>(check(view, check));
            view.cancel();
            refused.addAll(check(view, check));
            return refused;
        }));
        return found;
    }

    private static List<String> check(SetUpView view, Function<Node, List<String>> check) {
        SetUpFixture.texts(view);
        return check.apply(view);
    }
}
