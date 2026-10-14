package spike;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.awt.image.BufferedImage;
import java.io.File;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Question 4: one dashboard card in JavaFX + AtlantaFX (Primer), light and dark.
 * Args: --dark / --light (default: follow the system), --snapshot=file.png [--scale=2] renders and exits.
 * Made-up example data throughout.
 */
public class Dashboard extends Application {
    // example data
    static final double RISE = 7.0, SET = 19.6, PEAK_KW = 5.8;
    static final double WIN_START = 11.5, WIN_END = 15.0, NOW = 10.08;
    static final int BATTERY = 62, MIN = 20, TARGET = 80;

    static boolean dark;
    static Canvas chart;

    /** Started via {@link Card} because JavaFX refuses an Application subclass as main class on the class path. */

    @Override
    public void start(Stage stage) throws Exception {
        var named = getParameters().getNamed();
        var raw = getParameters().getRaw();
        dark = raw.contains("--dark") || (!raw.contains("--light")
                && Platform.getPreferences().getColorScheme() == ColorScheme.DARK);
        var scene = new Scene(build());
        applyTheme(scene, dark);
        Platform.getPreferences().colorSchemeProperty().addListener((o, a, b) -> applyTheme(scene, b == ColorScheme.DARK));
        stage.setTitle("Car charge planner");
        stage.getIcons().addAll(fxIcon(16), fxIcon(32), fxIcon(64), fxIcon(256));
        stage.setScene(scene);
        stage.show();
        System.out.println("font: " + Font.getDefault() + ", output scale " + stage.getOutputScaleX());

        String snap = named.get("snapshot");
        if (snap != null) {
            double scale = Double.parseDouble(named.getOrDefault("scale", "1"));
            Platform.runLater(() -> Platform.runLater(() -> {
                snapshot(scene.getRoot(), new File(snap), scale);
                Platform.exit();
            }));
        }
    }

    static Image fxIcon(int size) {
        return new Image(Dashboard.class.getResourceAsStream("icon-" + size + ".png"));
    }

    static void applyTheme(Scene scene, boolean isDark) {
        dark = isDark;
        Application.setUserAgentStylesheet(isDark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().setAll(Dashboard.class.getResource("dashboard.css").toExternalForm());
        scene.getRoot().getStyleClass().remove("dark");
        if (isDark) scene.getRoot().getStyleClass().add("dark");
        if (chart != null) drawChart();
    }

    static void snapshot(Node node, File file, double scale) {
        var params = new SnapshotParameters();
        params.setTransform(new Scale(scale, scale));
        var img = node.snapshot(params, null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var reader = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
        try {
            ImageIO.write(out, "png", file);
            System.out.println("snapshot written: " + file + " (" + w + "x" + h + ")");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- layout ------------------------------------------------------------------------------------

    public static Parent build() {
        var page = new VBox(header(), nextWindowCard(), solarCard(), batteryCard());
        page.getStyleClass().add("page");
        page.setPrefWidth(460);
        return page;
    }

    static Node header() {
        var icon = new ImageView(fxIcon(32));
        icon.setFitWidth(22);
        icon.setFitHeight(22);
        var title = new Label("CAR CHARGE PLANNER");
        title.getStyleClass().add("app-title");
        var toggle = new ToggleSwitch("Dark");
        toggle.setSelected(dark);
        toggle.selectedProperty().addListener((o, a, b) -> applyTheme(toggle.getScene(), b));
        var row = new HBox(10, icon, title, grow(), toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 4, 2, 4));
        return row;
    }

    static Node nextWindowCard() {
        var over = new Label("NEXT CHARGE WINDOW");
        over.getStyleClass().add("overline");
        var hero = new Label("Today 11:30 – 15:00");
        hero.getStyleClass().add("hero");
        var sub = new Label("Plug in before 11:30. Sunshine alone takes the car to its target.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        sub.setMinHeight(Region.USE_PREF_SIZE);
        var pills = new HBox(8,
                pill("sun", sunGlyph(), "18 kWh from sun"),
                pill("car", batteryGlyph(), "62 → 80 %"),
                pill("grid", boltGlyph(), "No grid"));
        var card = new VBox(over, hero, sub, pills);
        card.getStyleClass().add("card");
        VBox.setMargin(pills, new Insets(4, 0, 0, 0));
        return card;
    }

    static Node solarCard() {
        var title = new Label("Today's sun");
        title.getStyleClass().add("card-title");
        var peak = new Label("Peak 5.8 kW around 13:20");
        peak.getStyleClass().add("muted");
        var top = new HBox(title, grow(), peak);
        top.setAlignment(Pos.BASELINE_LEFT);

        chart = new Canvas(400, 170);
        var holder = new Pane(chart);
        holder.setMinHeight(170);
        holder.setPrefHeight(170);
        chart.widthProperty().bind(holder.widthProperty());
        chart.widthProperty().addListener((o, a, b) -> drawChart());

        var legend = new HBox(16,
                legend("-sun", "Solar forecast"),
                legend("-car", "Charge window"),
                legend("-color-fg-muted", "Now 10:05"));
        legend.setAlignment(Pos.CENTER_LEFT);
        var card = new VBox(top, holder, legend);
        card.getStyleClass().add("card");
        return card;
    }

    static Node batteryCard() {
        var title = new Label("Car battery");
        title.getStyleClass().add("card-title");
        var pct = new Label(BATTERY + " %");
        pct.getStyleClass().add("big-number");
        var top = new HBox(title, grow(), pct);
        top.setAlignment(Pos.CENTER_LEFT);

        var track = new Region();
        track.getStyleClass().add("battery-track");
        var fill = new Region();
        fill.getStyleClass().add("battery-fill");
        var min = new Region();
        min.getStyleClass().add("marker");
        var target = new Region();
        target.getStyleClass().add("marker");
        target.getStyleClass().add("target");
        var minLabel = new Label("min " + MIN + " %");
        minLabel.getStyleClass().add("marker-label");
        var targetLabel = new Label("target " + TARGET + " %");
        targetLabel.getStyleClass().add("marker-label");
        var bar = new Pane(track, fill, min, target, minLabel, targetLabel) {
            @Override
            protected void layoutChildren() {
                double w = getWidth(), barY = 4, barH = 16;
                track.resizeRelocate(0, barY, w, barH);
                fill.resizeRelocate(0, barY, w * BATTERY / 100.0, barH);
                double mx = w * MIN / 100.0, tx = w * TARGET / 100.0;
                min.resizeRelocate(mx - 1.5, 0, 3, barH + 8);
                target.resizeRelocate(tx - 1.5, 0, 3, barH + 8);
                double lw = minLabel.prefWidth(-1), tw = targetLabel.prefWidth(-1);
                minLabel.resizeRelocate(mx - lw / 2, barH + 12, lw, 18);
                targetLabel.resizeRelocate(tx - tw / 2, barH + 12, tw, 18);
            }
        };
        bar.setPrefHeight(48);
        bar.setMinHeight(48);
        var note = new Label("About 18 kWh to go. Ready by 15:00 if plugged in by 11:30.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        var card = new VBox(top, bar, note);
        card.getStyleClass().add("card");
        return card;
    }

    // ---- small parts -------------------------------------------------------------------------------

    static Region grow() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static Node pill(String kind, Node glyph, String text) {
        var label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        var box = new HBox(glyph, label);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.getStyleClass().addAll("pill", kind);
        return box;
    }

    static Node legend(String color, String text) {
        var dot = new Circle(4.5);
        dot.setStyle("-fx-fill: " + color + ";");
        var l = new Label(text);
        l.getStyleClass().add("muted");
        var box = new HBox(6, dot, l);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static Node sunGlyph() {
        var g = new Group();
        var c = new Circle(8, 8, 3.6);
        c.setStyle("-fx-fill: -sun;");
        g.getChildren().add(c);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            var l = new Line(8 + Math.cos(a) * 5.6, 8 + Math.sin(a) * 5.6, 8 + Math.cos(a) * 7.6, 8 + Math.sin(a) * 7.6);
            l.setStrokeWidth(1.6);
            l.setStrokeLineCap(StrokeLineCap.ROUND);
            l.setStyle("-fx-stroke: -sun;");
            g.getChildren().add(l);
        }
        return g;
    }

    static Node batteryGlyph() {
        var body = new Rectangle(1, 4, 12, 8);
        body.setArcWidth(3);
        body.setArcHeight(3);
        body.setStrokeWidth(1.5);
        body.setStyle("-fx-fill: transparent; -fx-stroke: -car;");
        var level = new Rectangle(3, 6, 6, 4);
        level.setStyle("-fx-fill: -car;");
        var tip = new Rectangle(13.5, 6.5, 1.8, 3);
        tip.setStyle("-fx-fill: -car;");
        return new Group(body, level, tip);
    }

    static Node boltGlyph() {
        var p = new Polygon(9, 1, 3, 9, 7.5, 9, 6, 15, 12.5, 6.5, 8, 6.5);
        p.setStyle("-fx-fill: -grid;");
        return p;
    }

    // ---- solar chart -------------------------------------------------------------------------------

    static double solar(double h) {
        if (h <= RISE || h >= SET) return 0;
        double x = (h - RISE) / (SET - RISE);
        double base = Math.pow(Math.sin(Math.PI * x), 1.5);
        double cloud = 1 - 0.18 * Math.exp(-Math.pow((h - 16.4) / 0.45, 2));
        return PEAK_KW * base * cloud;
    }

    static void drawChart() {
        var g = chart.getGraphicsContext2D();
        double w = chart.getWidth(), h = chart.getHeight();
        double left = 4, right = w - 4, top = 22, bottom = h - 26;
        double h0 = 5.5, h1 = 21.5;
        g.clearRect(0, 0, w, h);

        Color sun = Color.web(dark ? "#fbbf24" : "#f59e0b");
        Color sunLine = Color.web(dark ? "#fbbf24" : "#d97706");
        Color car = Color.web(dark ? "#4ade80" : "#16a34a");
        Color muted = Color.web(dark ? "#9198a1" : "#59636e");
        Color gridLine = Color.web(dark ? "#3d444d" : "#d1d9e0");
        Color fg = Color.web(dark ? "#f0f6fc" : "#1f2328");

        java.util.function.DoubleUnaryOperator X = t -> left + (t - h0) / (h1 - h0) * (right - left);
        java.util.function.DoubleUnaryOperator Y = kw -> bottom - kw / (PEAK_KW * 1.05) * (bottom - top);

        // charge window band
        double wx0 = X.applyAsDouble(WIN_START), wx1 = X.applyAsDouble(WIN_END);
        g.setFill(car.deriveColor(0, 1, 1, dark ? 0.10 : 0.07));
        fillRound(g, wx0, top - 8, wx1 - wx0, bottom - top + 8, 10);

        // baseline + hour labels
        g.setStroke(gridLine);
        g.setLineWidth(1);
        g.strokeLine(left, bottom + 0.5, right, bottom + 0.5);
        g.setFill(muted);
        g.setFont(Font.font(Font.getDefault().getFamily(), 11.5));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        for (int t = 6; t <= 21; t += 3) {
            g.fillText(String.format("%02d:00", t), X.applyAsDouble(t), bottom + 7);
        }

        // area under the solar curve, amber
        traceArea(g, X, Y, RISE, SET, bottom);
        g.setFill(new LinearGradient(0, top, 0, bottom, false, CycleMethod.NO_CYCLE,
                new Stop(0, sun.deriveColor(0, 1, 1, dark ? 0.45 : 0.38)), new Stop(1, sun.deriveColor(0, 1, 1, 0.04))));
        g.fill();

        // the part inside the charge window, green
        traceArea(g, X, Y, WIN_START, WIN_END, bottom);
        g.setFill(new LinearGradient(0, top, 0, bottom, false, CycleMethod.NO_CYCLE,
                new Stop(0, car.deriveColor(0, 1, 1, dark ? 0.55 : 0.45)), new Stop(1, car.deriveColor(0, 1, 1, 0.10))));
        g.fill();

        // curve strokes
        traceLine(g, X, Y, RISE, SET);
        g.setStroke(sunLine);
        g.setLineWidth(2.2);
        g.stroke();
        traceLine(g, X, Y, WIN_START, WIN_END);
        g.setStroke(car);
        g.setLineWidth(2.6);
        g.stroke();

        // window label
        g.setFill(car);
        g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 12));
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText("11:30 – 15:00", (wx0 + wx1) / 2, top - 10 + 12);

        // now line
        double nx = X.applyAsDouble(NOW);
        g.setStroke(fg.deriveColor(0, 1, 1, 0.6));
        g.setLineWidth(1.4);
        g.setLineDashes(3, 4);
        g.strokeLine(nx, top - 4, nx, bottom);
        g.setLineDashes();
        double ny = Y.applyAsDouble(solar(NOW));
        g.setFill(dark ? Color.web("#0d1117") : Color.WHITE);
        g.fillOval(nx - 5, ny - 5, 10, 10);
        g.setStroke(sunLine);
        g.setLineWidth(2.2);
        g.strokeOval(nx - 5, ny - 5, 10, 10);
        g.setFill(muted);
        g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 11.5));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText("now", nx, top - 6);
    }

    static void traceLine(GraphicsContext g, java.util.function.DoubleUnaryOperator X,
                          java.util.function.DoubleUnaryOperator Y, double from, double to) {
        g.beginPath();
        int n = 120;
        for (int i = 0; i <= n; i++) {
            double t = from + (to - from) * i / n;
            double x = X.applyAsDouble(t), y = Y.applyAsDouble(solar(t));
            if (i == 0) g.moveTo(x, y); else g.lineTo(x, y);
        }
    }

    static void traceArea(GraphicsContext g, java.util.function.DoubleUnaryOperator X,
                          java.util.function.DoubleUnaryOperator Y, double from, double to, double bottom) {
        traceLine(g, X, Y, from, to);
        g.lineTo(X.applyAsDouble(to), bottom);
        g.lineTo(X.applyAsDouble(from), bottom);
        g.closePath();
    }

    static void fillRound(GraphicsContext g, double x, double y, double w, double h, double r) {
        g.fillRoundRect(x, y, w, h, r * 2, r * 2);
    }
}
