package spike;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** The app icon at a given pixel size, optionally with a coloured status dot (bottom right). */
final class Icons {
    static final Color GREEN = new Color(0x22, 0xc5, 0x5e);
    static final Color AMBER = new Color(0xf5, 0x9e, 0x0b);

    static BufferedImage icon(int size, Color dot) {
        int src = size <= 16 ? 16 : size <= 22 ? 22 : size <= 24 ? 24 : size <= 32 ? 32
                : size <= 44 ? 44 : size <= 48 ? 48 : size <= 64 ? 64 : size <= 128 ? 128 : 256;
        BufferedImage base;
        try (var in = Icons.class.getResourceAsStream("icon-" + src + ".png")) {
            base = ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(base, 0, 0, size, size, null);
        if (dot != null) {
            int d = Math.max(6, size * 7 / 16);
            int ring = Math.max(1, size / 16);
            g.setColor(Color.WHITE);
            g.fillOval(size - d - 1, size - d - 1, d + 1, d + 1);
            g.setColor(dot);
            g.fillOval(size - d - 1 + ring, size - d - 1 + ring, d + 1 - 2 * ring, d + 1 - 2 * ring);
        }
        g.dispose();
        return img;
    }
}
