package nl.enadeag.carchargeplanner.desktop;

import com.sun.javafx.tk.Toolkit;
import nl.enadeag.carchargeplanner.desktop.theme.SystemTheme;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Takes pictures of the app's screens on the headless platform, for the documentation, and writes them as PNG files
 * that change only when their pixels do.
 */
public final class Pictures {

    /** The headless window the pictures are taken in: small, so it never reaches past the 1000 px headless screen. */
    private static final double WINDOW = 100;

    private Pictures() {
    }

    /**
     * What a picture shows around the screen: a window's background, or nothing, as around the popup's rounded
     * corners.
     */
    public enum Frame {
        /** The window's own background, as a window shows. */
        WINDOW,
        /** See-through, as the popup's transparent window. */
        POPUP
    }

    /**
     * A screen as the app shows it, in a focused headless window, so the control with the focus shows its ring. The
     * screen may be larger than the headless screen: it is laid out at its size outside the small window, and drawn
     * whole. Make, use and close it on the JavaFX thread.
     */
    public static final class Studio {

        private final StackPane holder;
        private final Stage stage = new Stage();
        private final double zoom;
        private final double width;
        /** Null for as tall as the screen asks at its width. */
        private final Double height;

        /**
         * Shows a screen.
         *
         * @param screen what to take a picture of
         * @param scheme light or dark
         * @param frame what shows around and behind the screen
         * @param zoom the system's display scaling: 1 for 100 %, 2 for 200 %
         * @param width the screen's width, in pixels at 100 %
         * @param height the screen's height, in pixels at 100 %; empty for as tall as the screen asks at that width
         */
        public Studio(Region screen, ColorScheme scheme, Frame frame, double zoom, double width,
                Optional<Double> height) {
            this.zoom = zoom;
            this.width = width;
            this.height = height.orElse(null);
            holder = new StackPane(screen);
            if (frame == Frame.WINDOW) {
                holder.setStyle("-fx-background-color: -color-bg-default;");
            }
            if (frame == Frame.POPUP) {
                holder.getStyleClass().add("popup");
            }
            // the scene's root keeps the theme's "root" and "dark"; the holder is laid out at the picture's size
            var root = new Group(holder);
            root.setAutoSizeChildren(false);
            var scene = new Scene(root, WINDOW, WINDOW, Color.TRANSPARENT);
            new SystemTheme(new SimpleObjectProperty<>(scheme)).style(scene);
            stage.setScene(scene);
            stage.show();
            stage.requestFocus();
            layOut();
        }

        /**
         * Takes the picture of the screen as it is now.
         *
         * @return the picture, see-through where the screen draws nothing
         */
        public WritableImage take() {
            // the caret blinks on a timer, which would make the picture differ from run to run
            holder.lookupAll(".text-input").forEach(field -> field.setStyle(
                    Objects.requireNonNullElse(field.getStyle(), "") + "-fx-display-caret: false;"));
            layOut();
            var parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            // drawn larger, as a system with display scaling draws the window: sharp, not a smaller picture blown up
            parameters.setTransform(Transform.scale(zoom, zoom));
            return holder.snapshot(parameters, null);
        }

        /** Closes the window. */
        public void close() {
            stage.close();
        }

        private void layOut() {
            // what a pulse does, now rather than whenever the next comes: a scene without a focus owner gives the
            // focus to its first control, as the app's window does when it opens
            Toolkit.getToolkit().firePulse();
            // a scroll pane draws its page from a cache that only a pulse renews; the picture should not wait for one
            holder.lookupAll(".scroll-pane > .viewport").forEach(viewport -> viewport.setCache(false));
            holder.applyCss();
            var focused = holder.getScene().getFocusOwner();
            if (focused != null) {
                focused.applyCss(); // a node on a scroll pane's page takes its new focus state only so
            }
            // twice: a page that scrolls knows its height only once it is laid out
            for (int i = 0; i < 2; i++) {
                holder.resize(width, height != null ? height : holder.prefHeight(width));
                holder.layout();
            }
        }
    }

    /**
     * Puts pictures side by side, top-aligned, with a see-through gap between them.
     *
     * @param gap the gap in pixels
     * @param pictures the pictures, left to right
     * @return one picture as wide as all with their gaps, and as tall as the tallest
     */
    public static WritableImage sideBySide(int gap, Image... pictures) {
        int width = Arrays.stream(pictures).mapToInt(picture -> (int) picture.getWidth()).sum()
                + gap * (pictures.length - 1);
        int height = Arrays.stream(pictures).mapToInt(picture -> (int) picture.getHeight()).max().orElse(0);
        var joined = new WritableImage(width, height);
        int x = 0;
        for (var picture : pictures) {
            int w = (int) picture.getWidth();
            int h = (int) picture.getHeight();
            joined.getPixelWriter().setPixels(x, 0, w, h, picture.getPixelReader(), 0, 0);
            x += w + gap;
        }
        return joined;
    }

    /**
     * Writes a picture as a PNG file unless the file holds the same pixels already, so a picture that did not change
     * leaves its file, and git, alone. The PNG holds only the pixels: no time, no other metadata, so the same picture
     * always makes the same file.
     *
     * @param picture the picture
     * @param file where it goes
     * @return true when the file was written, false when it held the same picture
     * @throws IOException when the file cannot be read or written
     */
    public static boolean write(Image picture, Path file) throws IOException {
        var image = toBuffered(picture);
        if (Files.isRegularFile(file) && samePixels(image, read(file))) {
            return false;
        }
        var folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        // a new file with a name nobody can guess, so the write cannot be led through a planted link
        var temporary = Files.createTempFile(folder, file.getFileName().toString(), ".tmp");
        try {
            try (var out = Files.newOutputStream(temporary)) {
                if (!ImageIO.write(image, "png", out)) {
                    throw new IOException("no PNG writer");
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        return true;
    }

    /**
     * Reads a PNG file's pixels.
     *
     * @param file the file
     * @return its picture, or null when it is no picture ImageIO can read
     * @throws IOException when the file cannot be read
     */
    public static BufferedImage read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return ImageIO.read(in);
        }
    }

    /** Whether two pictures have the same size and the same colour, with transparency, in every pixel. */
    public static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        int width = a.getWidth();
        int height = a.getHeight();
        return Arrays.equals(a.getRGB(0, 0, width, height, null, 0, width),
                b.getRGB(0, 0, width, height, null, 0, width));
    }

    /** The picture as AWT's, without an alpha channel when every pixel is opaque, which makes the file smaller. */
    private static BufferedImage toBuffered(Image picture) {
        int width = (int) picture.getWidth();
        int height = (int) picture.getHeight();
        var argb = new int[width * height];
        picture.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        boolean opaque = Arrays.stream(argb).allMatch(pixel -> pixel >>> 24 == 0xff);
        var image = new BufferedImage(width, height, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }
}
