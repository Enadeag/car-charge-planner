package nl.enadeag.carchargeplanner.desktop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

class StatusIconTest {

    private static final StatusIcon ICON = new StatusIcon();

    /** Where the pixel lies in an icon of this size, given on the 64 by 64 grid of the drawing. */
    private static Color pixel(BufferedImage image, double x, double y) {
        double scale = image.getWidth() / 64.0;
        return new Color(image.getRGB((int) (x * scale), (int) (y * scale)), true);
    }

    private static boolean isLight(Color colour) {
        return colour.getAlpha() == 255 && colour.getRed() + colour.getGreen() + colour.getBlue() > 3 * 200;
    }

    private static boolean isDark(Color colour) {
        return colour.getAlpha() == 255 && colour.getRed() + colour.getGreen() + colour.getBlue() < 3 * 60;
    }

    private static Element dotOf(String svg) throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        var circles = document.getElementsByTagName("circle");
        // the first circle cuts the gap in the mask; the dot is drawn after the shapes
        return circles.getLength() < 2 ? null : (Element) circles.item(circles.getLength() - 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 20, 22, 24, 32, 48, 64})
    void theIconIsDrawnAtTheSizeAskedForAndTransparentAroundTheShapes(int size) {
        var image = ICON.image(StatusDot.NONE, PanelShade.DARK, size);

        assertThat(image.getWidth()).isEqualTo(size);
        assertThat(image.getHeight()).isEqualTo(size);
        assertThat(pixel(image, 1, 1).getAlpha()).isZero();
    }

    @Test
    void theShapesAreLightOnADarkPanelAndDarkOnALightOne() {
        // the middle of the sun
        assertThat(pixel(ICON.image(StatusDot.NONE, PanelShade.DARK, 32), 32, 26)).matches(StatusIconTest::isLight);
        assertThat(pixel(ICON.image(StatusDot.NONE, PanelShade.LIGHT, 32), 32, 26)).matches(StatusIconTest::isDark);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDot.class, names = {"SUN", "GRID", "ACTION"})
    void theDotIsSetApartFromTheShapesByAClearRing(StatusDot dot) {
        var image = ICON.image(dot, PanelShade.DARK, 64);

        assertThat(pixel(image, 52, 52).getAlpha()).as("the dot").isEqualTo(255);
        // on the ground line, which runs under the ring
        assertThat(pixel(image, 52, 38).getAlpha()).as("the ring").isZero();
        assertThat(pixel(ICON.image(StatusDot.NONE, PanelShade.DARK, 64), 52, 38).getAlpha())
                .as("the ground line without a dot").isEqualTo(255);
    }

    @Test
    void eachDotHasAColourOfItsOwnAndNoDotLeavesTheCornerEmpty() {
        var sun = pixel(ICON.image(StatusDot.SUN, PanelShade.DARK, 32), 52, 52);
        var grid = pixel(ICON.image(StatusDot.GRID, PanelShade.DARK, 32), 52, 52);
        var action = pixel(ICON.image(StatusDot.ACTION, PanelShade.DARK, 32), 52, 52);

        assertThat(sun.getRed()).as("amber").isGreaterThan(sun.getBlue() + 100);
        assertThat(grid.getBlue()).as("blue").isGreaterThan(grid.getRed() + 100);
        assertThat(action.getRed()).as("red").isGreaterThan(action.getGreen() + 100);
        assertThat(pixel(ICON.image(StatusDot.NONE, PanelShade.DARK, 32), 52, 52).getAlpha()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = StatusDot.class, names = {"SUN", "GRID", "ACTION"})
    void theSvgShowsTheSameDotAsThePixels(StatusDot dot) throws Exception {
        var drawn = pixel(ICON.image(dot, PanelShade.DARK, 64), 52, 52);

        var circle = dotOf(ICON.svg(dot, PanelShade.DARK));

        assertThat(Color.decode(circle.getAttribute("fill")).getRGB()).isEqualTo(drawn.getRGB());
        assertThat(circle.getAttribute("cx")).isEqualTo("52");
        assertThat(circle.getAttribute("r")).isEqualTo("12");
    }

    @Test
    void theSvgWithoutADotHasNone() throws Exception {
        assertThat(dotOf(ICON.svg(StatusDot.NONE, PanelShade.DARK))).isNull();
    }

    @Test
    void theSvgLetsKdeColourTheShapesAsItsPanelsText() {
        // Plasma swaps the colour in the style with this id for its panel's text colour
        assertThat(ICON.svg(StatusDot.SUN, PanelShade.DARK))
                .contains("<style id=\"current-color-scheme\">")
                .contains("class=\"ColorScheme-Text\" fill=\"currentColor\"");
    }

    @Test
    void anIconTooSmallForItsDotIsRefused() {
        assertThatThrownBy(() -> ICON.image(StatusDot.SUN, PanelShade.DARK, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
