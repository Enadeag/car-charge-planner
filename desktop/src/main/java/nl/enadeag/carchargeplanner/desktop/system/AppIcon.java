package nl.enadeag.carchargeplanner.desktop.system;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

/** The app icon, drawn for each of the sizes trays and title bars ask for. */
public final class AppIcon {

    /** The sizes the icon is drawn in, in pixels, smallest first; {@link #image(int)} accepts only these. */
    public static final List<Integer> SIZES = List.of(16, 22, 24, 32, 48, 64, 128, 256);

    /** Makes the icon; its images are read from the app's resources when asked for. */
    public AppIcon() {
    }

    /**
     * The icon drawn for one size, for AWT and the tray protocols.
     *
     * @param size the width and height in pixels; must be one of {@link #SIZES}
     * @return a new image of {@code size} by {@code size} pixels, with transparency
     * @throws IllegalArgumentException when there is no icon of that size
     * @throws UncheckedIOException when the image cannot be read from the app's resources
     */
    public BufferedImage image(int size) {
        try (var in = resource(size).openStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The icon in every size, for a JavaFX window's title bar and taskbar entry, which pick the size they need.
     *
     * @return one image per size in {@link #SIZES}, in that order
     */
    public List<Image> fxImages() {
        return SIZES.stream().map(size -> new Image(resource(size).toExternalForm())).toList();
    }

    private static URL resource(int size) {
        if (!SIZES.contains(size)) {
            throw new IllegalArgumentException("no icon of " + size + " px");
        }
        var name = "icons/icon-" + size + ".png";
        return Objects.requireNonNull(AppIcon.class.getResource(name), name + " is missing from the app");
    }
}
