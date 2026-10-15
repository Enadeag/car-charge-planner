package io.github.enadeag.carchargeplanner.desktop;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

/** The app icon, drawn for each of the sizes trays and title bars ask for. */
public final class AppIcon {

    public static final List<Integer> SIZES = List.of(16, 22, 24, 32, 48, 64, 128, 256);

    public BufferedImage image(int size) {
        try (var in = resource(size).openStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Image> fxImages() {
        return SIZES.stream().map(size -> new Image(resource(size).toExternalForm())).toList();
    }

    private static URL resource(int size) {
        if (!SIZES.contains(size)) {
            throw new IllegalArgumentException("no icon of " + size + " px");
        }
        return AppIcon.class.getResource("icons/icon-" + size + ".png");
    }
}
