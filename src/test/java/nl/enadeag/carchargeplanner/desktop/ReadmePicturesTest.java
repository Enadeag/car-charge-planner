package nl.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.application.ColorScheme;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The documentation's pictures are made by code, so they cannot fall behind the screens unnoticed: every picture the
 * README and the pages in {@code docs/} show is made, in light and in dark, the same on every run, and a file is
 * written only when its picture changed.
 */
@FxTest
class ReadmePicturesTest {

    /** The folder whose pages show pictures besides the README; its subfolders hold history and are left out. */
    private static final Path DOCS = Path.of("docs");

    /** A picture a page names by a relative path, in an HTML attribute or a Markdown image; not a web address. */
    private static final Pattern PICTURE = Pattern.compile("[\"(]([^\"()\\s:]+\\.png)[\")]");

    @Test
    void makesEveryPictureTheDocumentationShowsInLightAndDarkAndTheSameAgain(@TempDir Path into, @TempDir Path work)
            throws Exception {
        var written = ReadmePictures.make(into, Files.createDirectory(work.resolve("first")));

        assertThat(written).containsAll(shownPictures());
        assertThat(written).hasSize(2 * ReadmePictures.PICTURES.size());
        for (var name : ReadmePictures.PICTURES.keySet()) {
            var light = Pictures.read(into.resolve(ReadmePictures.fileName(name, ColorScheme.LIGHT)));
            var dark = Pictures.read(into.resolve(ReadmePictures.fileName(name, ColorScheme.DARK)));
            assertThat(colours(light)).as(name + " shows more than a blank screen").isGreaterThan(20);
            assertThat(Pictures.samePixels(light, dark)).as(name + " differs in dark").isFalse();
        }
        var before = contents(into);

        assertThat(ReadmePictures.make(into, Files.createDirectory(work.resolve("again")))).isEmpty();
        assertThat(contents(into)).isEqualTo(before);
    }

    @Test
    void aFileIsReplacedOnlyWhenItsPixelsChange(@TempDir Path folder) throws Exception {
        var file = folder.resolve("picture.png");
        var picture = picture(Color.TEAL);
        assertThat(Pictures.write(picture, file)).isTrue();
        // the same pixels, encoded differently: as another program might have saved them
        ImageIO.write(withAlpha(Pictures.read(file)), "png", file.toFile());
        var reEncoded = Files.readAllBytes(file);

        assertThat(Pictures.write(picture, file)).isFalse();
        assertThat(Files.readAllBytes(file)).isEqualTo(reEncoded);

        var changed = picture(Color.TEAL);
        changed.getPixelWriter().setColor(3, 2, Color.ORANGE);
        assertThat(Pictures.write(changed, file)).isTrue();
        assertThat(Pictures.read(file).getRGB(3, 2)).isEqualTo(0xffffa500);
        assertThat(Pictures.read(file).getRGB(0, 0)).isEqualTo(0xff008080);
    }

    @Test
    void aPictureFileHoldsOnlyItsPixelsAndIsTheSameEachTime(@TempDir Path folder) throws Exception {
        var first = folder.resolve("first.png");
        var second = folder.resolve("second.png");
        Pictures.write(picture(Color.TEAL), first);
        Pictures.write(picture(Color.TEAL), second);

        assertThat(chunks(first)).containsExactly("IHDR", "IDAT", "IEND");
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }

    /** Makes the pictures in the repository again; only {@code ./mvnw test -Pscreenshots} runs it. */
    @Test
    @Tag("screenshots")
    void makeThePictures(@TempDir Path work) throws Exception {
        var written = ReadmePictures.make(ReadmePictures.FOLDER, work);
        System.out.println(written.isEmpty() ? "No picture changed." : "Changed: " + String.join(", ", written));
    }

    /** The file names of the pictures the README and the pages in {@code docs/} show; each must be in the folder. */
    private static List<String> shownPictures() throws IOException {
        var pages = new ArrayList<Path>();
        pages.add(Path.of("README.md"));
        try (Stream<Path> files = Files.list(DOCS)) {
            files.filter(file -> file.getFileName().toString().endsWith(".md")).sorted().forEach(pages::add);
        }
        var found = new ArrayList<String>();
        for (var page : pages) {
            var folder = page.toAbsolutePath().getParent();
            var matcher = PICTURE.matcher(Files.readString(page));
            while (matcher.find()) {
                var picture = folder.resolve(matcher.group(1)).normalize();
                assertThat(picture.getParent()).as(page + " names " + matcher.group(1))
                        .isEqualTo(ReadmePictures.FOLDER.toAbsolutePath());
                found.add(picture.getFileName().toString());
            }
        }
        assertThat(found).as("the pages show pictures").isNotEmpty();
        return found;
    }

    private static long colours(BufferedImage image) {
        int width = image.getWidth();
        return Arrays.stream(image.getRGB(0, 0, width, image.getHeight(), null, 0, width)).distinct().count();
    }

    /** Each file's name and bytes, as text that compares by value. */
    private static List<String> contents(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.sorted().map(file -> {
                try {
                    return file.getFileName() + " " + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        }
    }

    private static WritableImage picture(Color colour) throws Exception {
        return Fx.call(() -> {
            var picture = new WritableImage(8, 6);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 6; y++) {
                    picture.getPixelWriter().setColor(x, y, colour);
                }
            }
            return picture;
        });
    }

    private static BufferedImage withAlpha(BufferedImage image) {
        var copy = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(image, 0, 0, null);
        return copy;
    }

    /** The types of a PNG file's chunks, in order. */
    private static List<String> chunks(Path file) throws IOException {
        var types = new ArrayList<String>();
        try (var in = new DataInputStream(Files.newInputStream(file))) {
            in.skipNBytes(8);
            while (in.available() > 0) {
                int length = in.readInt();
                var type = new ByteArrayOutputStream();
                type.write(in.readNBytes(4));
                types.add(type.toString(StandardCharsets.US_ASCII));
                in.skipNBytes(length + 4L);
            }
        }
        return types;
    }
}
