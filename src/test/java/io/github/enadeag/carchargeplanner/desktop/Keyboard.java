package io.github.enadeag.carchargeplanner.desktop;

import com.sun.javafx.stage.WindowHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;

/**
 * Presses keys as the owner does, through JavaFX's robot on the headless platform: a key reaches the focused window,
 * its focus owner and the scene's default and cancel buttons, as a real key does. Walks a screen with Tab and checks
 * what someone who uses only the keyboard, or a screen reader, depends on.
 */
public final class Keyboard {

    /** More stops than any screen has: a walk that does not come round by then is stuck. */
    private static final int MOST_STOPS = 120;
    /** How far outside a control its focus ring may be drawn, in pixels. */
    private static final int RING_REACH = 6;
    /** WCAG 2.2's minimum for a focus indicator against what it replaces, as for any mark. */
    private static final double RING_CONTRAST = 3;

    private Keyboard() {
    }

    public static void press(KeyCode key) throws Exception {
        Fx.run(() -> {
            var robot = new Robot();
            robot.keyPress(key);
            robot.keyRelease(key);
        });
    }

    /** Presses a key in a window, which takes the focus first, as a click on it would. */
    public static void press(Scene scene, KeyCode key) throws Exception {
        Fx.run(() -> scene.getWindow().requestFocus());
        Fx.settle();
        press(key);
    }

    public static void pressWithShift(KeyCode key) throws Exception {
        Fx.run(() -> {
            var robot = new Robot();
            robot.keyPress(KeyCode.SHIFT);
            robot.keyPress(key);
            robot.keyRelease(key);
            robot.keyRelease(KeyCode.SHIFT);
        });
    }

    /**
     * One stop of the Tab key: the control that has the focus, and the window as it looks with it and without it, at
     * the same scroll position.
     */
    private record Stop(Node node, Image with, Image without, Bounds ringBounds) {
    }

    /**
     * Walks a screen with Tab until the focus comes round, and says what is wrong for someone who uses the keyboard
     * or a screen reader: a control Tab never reaches, a stop out of reading order, a control a screen reader on
     * Windows has no name for, two stops it calls the same, a focus ring that does not stand out 3:1 from the look
     * without the focus, and Shift+Tab not going back. Tab stuck in one place, or never coming round, fails at once.
     *
     * @param scene the scene of the focused window; its window should be large enough that its pages do not scroll
     * @param root the part of the scene to check, such as the view inside a test's wrapper
     * @return the problems, in words; empty when there are none
     */
    public static List<String> problems(Scene scene, Node root) throws Exception {
        Fx.run(() -> WindowHelper.setFocused(scene.getWindow(), true));
        Fx.call(() -> look(scene)); // a fresh window resolves its theme's styles on its first look
        var stops = tabRound(scene);
        var problems = new ArrayList<String>();
        problems.addAll(Fx.call(() -> unreached(root, stops)));
        problems.addAll(Fx.call(() -> outOfOrder(root, stops)));
        problems.addAll(Fx.call(() -> unnamed(stops)));
        problems.addAll(Fx.call(() -> faintRings(stops)));
        pressWithShift(KeyCode.TAB);
        if (Fx.call(scene::getFocusOwner) != stops.getLast().node()) {
            problems.add("Shift+Tab from the first stop does not go back to the last");
        }
        return problems;
    }

    /**
     * Presses Tab until the focus comes round to where the first press put it.
     *
     * @throws AssertionError when Tab stops moving the focus, or the focus does not come round: a keyboard trap
     */
    private static List<Stop> tabRound(Scene scene) throws Exception {
        var stops = new ArrayList<Stop>();
        press(scene, KeyCode.TAB);
        var first = Fx.call(scene::getFocusOwner);
        if (first == null) {
            throw new AssertionError("Tab put the focus nowhere");
        }
        stops.add(stop(scene));
        while (stops.size() <= MOST_STOPS) {
            press(KeyCode.TAB);
            var at = Fx.call(scene::getFocusOwner);
            if (at == first) {
                return stops;
            }
            if (at == null || at == stops.getLast().node()) {
                throw new AssertionError("Tab is stuck after " + Fx.call(() -> describe(stops.getLast().node())));
            }
            stops.add(stop(scene));
        }
        throw new AssertionError("Tab did not come round in " + MOST_STOPS + " stops");
    }

    /** The window with the focus where it is, and without the focus: a window that is not focused shows no ring. */
    private static Stop stop(Scene scene) throws Exception {
        return Fx.call(() -> {
            var node = scene.getFocusOwner();
            look(scene); // a scroll pane brings the focused control into view as it lays out
            var bounds = sceneBounds(AccessibleNames.focusTarget(node));
            var with = look(scene);
            WindowHelper.setFocused(scene.getWindow(), false);
            var without = look(scene);
            WindowHelper.setFocused(scene.getWindow(), true);
            if (!bounds.equals(sceneBounds(AccessibleNames.focusTarget(node)))) {
                throw new AssertionError(describe(node) + " moved while its focus ring was compared");
            }
            return new Stop(node, with, without, bounds);
        });
    }

    private static Image look(Scene scene) {
        // a scroll pane draws its page from a cache that only a pulse renews; the snapshot should not wait for one
        scene.getRoot().lookupAll(".scroll-pane > .viewport").forEach(viewport -> viewport.setCache(false));
        scene.getRoot().applyCss();
        if (scene.getFocusOwner() != null) {
            scene.getFocusOwner().applyCss(); // a node on a scroll pane's page takes its new focus state only so
        }
        scene.getRoot().layout();
        return scene.snapshot(null);
    }

    /**
     * Controls on the screen that Tab should reach and did not; what a control is made of is its own, and Tab stops
     * at one radio button of a group, where the arrow keys choose.
     */
    private static List<String> unreached(Node root, List<Stop> stops) {
        var reached = stops.stream().map(Stop::node).toList();
        var groups = reached.stream().flatMap(node -> node instanceof RadioButton radio && radio.getToggleGroup() != null
                ? Stream.of(radio.getToggleGroup()) : Stream.empty()).toList();
        return Scenes.all(root).stream()
                .filter(node -> node.isFocusTraversable() && !node.isDisabled() && Scenes.reallyVisible(node))
                .filter(node -> !insideAControl(node, root))
                .filter(node -> !reached.contains(node))
                .filter(node -> !(node instanceof RadioButton radio && groups.contains(radio.getToggleGroup())))
                .map(node -> "Tab never reaches " + describe(node))
                .toList();
    }

    private static boolean insideAControl(Node node, Node root) {
        for (var at = node.getParent(); at != null && at != root.getParent(); at = at.getParent()) {
            if (at instanceof Control && !(at instanceof ScrollPane)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The stops in reading order: each one lower than the one before, or on the same line to its right. Where the
     * round starts is Tab's own choice, so it counts from where it reads best.
     */
    private static List<String> outOfOrder(Node root, List<Stop> stops) {
        var panes = Scenes.all(root).stream().filter(ScrollPane.class::isInstance).map(ScrollPane.class::cast)
                .filter(Scenes::reallyVisible).toList();
        var bounds = stops.stream().map(stop -> unrolled(AccessibleNames.focusTarget(stop.node()), panes)).toList();
        List<String> best = null;
        for (int start = 0; start < stops.size(); start++) {
            var problems = new ArrayList<String>();
            for (int k = 1; k < stops.size(); k++) {
                int from = (start + k - 1) % stops.size();
                int to = (start + k) % stops.size();
                var a = bounds.get(from);
                var b = bounds.get(to);
                boolean lower = b.getMinY() >= a.getMinY() - 1;
                boolean sameLineRight = b.getMinY() < a.getMaxY() && b.getMinX() > a.getMinX();
                if (!lower && !sameLineRight) {
                    problems.add("Tab goes back up from " + describe(stops.get(from).node()) + " to "
                            + describe(stops.get(to).node()));
                }
            }
            if (best == null || problems.size() < best.size()) {
                best = problems;
            }
        }
        return best;
    }

    /** A name for every stop, and no two stops with the same name, as a screen reader on Windows reads them. */
    private static List<String> unnamed(List<Stop> stops) {
        var problems = new ArrayList<String>();
        var seen = new HashMap<String, Node>();
        for (var stop : stops) {
            var target = AccessibleNames.focusTarget(stop.node());
            var name = AccessibleNames.of(target);
            if (name.isEmpty()) {
                problems.add("a screen reader has no name for " + describe(stop.node()));
            } else if (seen.putIfAbsent(name, target) != null) {
                problems.add("a screen reader reads two controls as '" + name + "'");
            }
        }
        return problems;
    }

    /**
     * Every stop's focus ring: where the window with the focus there differs from the window without the focus by
     * 3:1 or more, near the control, those pixels together are at least as many as a 1 px line round the control.
     */
    private static List<String> faintRings(List<Stop> stops) {
        var problems = new ArrayList<String>();
        for (var stop : stops) {
            var bounds = stop.ringBounds();
            int perimeter = (int) (2 * (bounds.getWidth() + bounds.getHeight()));
            int changed = changedPixels(stop.with(), stop.without(), bounds);
            if (changed < perimeter) {
                problems.add("the focus ring of %s changes %d pixels by 3:1, fewer than its %d px outline"
                        .formatted(describe(stop.node()), changed, perimeter));
            }
        }
        return problems;
    }

    private static int changedPixels(Image focused, Image unfocused, Bounds bounds) {
        int left = (int) Math.max(0, Math.floor(bounds.getMinX()) - RING_REACH);
        int top = (int) Math.max(0, Math.floor(bounds.getMinY()) - RING_REACH);
        int right = (int) Math.min(focused.getWidth(), Math.ceil(bounds.getMaxX()) + RING_REACH);
        int bottom = (int) Math.min(focused.getHeight(), Math.ceil(bounds.getMaxY()) + RING_REACH);
        var with = focused.getPixelReader();
        var without = unfocused.getPixelReader();
        int changed = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if (with.getArgb(x, y) != without.getArgb(x, y)
                        && Contrast.ratio(with.getColor(x, y), without.getColor(x, y)) >= RING_CONTRAST) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    /**
     * Where a node would be in a window tall enough for every scroll pane to show its whole page at once: a node on
     * a page lies where it would without scrolling, and one below a scroll pane lies lower by what the page hides.
     */
    private static Bounds unrolled(Node node, List<ScrollPane> panes) {
        var bounds = sceneBounds(node);
        double down = 0;
        for (var pane : panes) {
            var viewport = sceneBounds(pane.lookup(".viewport"));
            var page = pane.getContent();
            if (page == null) {
                continue;
            }
            if (inside(node, pane)) {
                var onPage = page.sceneToLocal(bounds);
                bounds = new BoundingBox(onPage.getMinX() + viewport.getMinX(), onPage.getMinY() + viewport.getMinY(),
                        onPage.getWidth(), onPage.getHeight());
            } else if (bounds.getMinY() >= viewport.getMaxY() - 1 && bounds.getMinX() < viewport.getMaxX()
                    && bounds.getMaxX() > viewport.getMinX()) {
                down += Math.max(0, page.getLayoutBounds().getHeight() - viewport.getHeight());
            }
        }
        return new BoundingBox(bounds.getMinX(), bounds.getMinY() + down, bounds.getWidth(), bounds.getHeight());
    }

    private static boolean inside(Node node, Node ancestor) {
        for (var at = node.getParent(); at != null; at = at.getParent()) {
            if (at == ancestor) {
                return true;
            }
        }
        return false;
    }

    /** A node as a failure names it: its class, its style classes and what a screen reader calls it. */
    public static String describe(Node node) {
        return node.getClass().getSimpleName() + node.getStyleClass() + " '"
                + AccessibleNames.of(AccessibleNames.focusTarget(node)) + "'";
    }
}
