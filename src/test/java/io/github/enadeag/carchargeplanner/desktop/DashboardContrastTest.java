package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * WCAG AA on the rendered dashboard, in light and in dark: every text reaches 4.5:1 against what it sits on, every
 * glyph 3:1. Controls that are switched off are exempt, as WCAG allows.
 */
class DashboardContrastTest {

    private static final double TEXT = 4.5;
    private static final double MARK = 3;

    private Stage stage;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    /** Every day of the example plan, and the message shown without a plan. */
    private List<String> problems(ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var view = new DashboardView(() -> { }, () -> { });
            stage = Scenes.show(view, scheme);
            var problems = new ArrayList<String>();
            var plan = Models.plan(Optional.of(Models.ADVICE));
            for (int day = 0; day < plan.days().size(); day++) {
                view.show(plan);
                ((javafx.scene.control.TabPane) view.lookup(".day-tabs")).getSelectionModel().select(day);
                view.applyCss();
                problems.addAll(problemsIn(view));
            }
            view.show(new DashboardModel.Message("No forecast", "Could not reach Open-Meteo", "Forecast: Open-Meteo"));
            view.applyCss();
            problems.addAll(problemsIn(view));
            return problems;
        });
    }

    private static List<String> problemsIn(Node root) {
        var problems = new ArrayList<String>();
        for (var node : Scenes.all(root)) {
            if (!Scenes.reallyVisible(node) || node.isDisabled()) {
                continue;
            }
            switch (node) {
                case Labeled labeled when !labeled.getText().isEmpty() ->
                        check(problems, labeled.getText(), labeled.getTextFill(), background(node), TEXT);
                case Shape shape when shape.getStyleClass().contains("glyph-fill") ->
                        check(problems, "glyph in " + owner(node), shape.getFill(), background(node), MARK);
                case Shape shape when shape.getStyleClass().contains("glyph-stroke") ->
                        check(problems, "glyph in " + owner(node), shape.getStroke(), background(node), MARK);
                default -> { }
            }
        }
        return problems;
    }

    private static void check(List<String> problems, String what, Object paint, Color background, double needed) {
        var colour = (Color) paint;
        double ratio = Contrast.ratio(Contrast.over(colour, background), background);
        if (ratio < needed) {
            problems.add("%s: %.2f:1, %s on %s".formatted(what, ratio, colour, background));
        }
    }

    /** The colour behind a node: the backgrounds of the regions it lies in, painted over each other. */
    private static Color background(Node node) {
        var regions = new ArrayList<Region>();
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof Region region) {
                regions.addFirst(region);
            }
        }
        var colour = Color.WHITE;
        for (var region : regions) {
            if (region.getBackground() == null) {
                continue;
            }
            for (var fill : region.getBackground().getFills()) {
                if (fill.getFill() instanceof Color paint) {
                    colour = Contrast.over(paint, colour);
                }
            }
        }
        return colour;
    }

    private static String owner(Node node) {
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (!at.getStyleClass().isEmpty() && !at.getStyleClass().contains("glyph")) {
                return at.getStyleClass().toString();
            }
        }
        return "?";
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyTextAndGlyphStandsOutEnough(ColorScheme scheme) throws Exception {
        assertThat(problems(scheme)).isEmpty();
    }
}
