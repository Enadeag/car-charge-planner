package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClosingOrderTest {

    private final ClosingOrder closing = new ClosingOrder();
    private final List<String> closed = new ArrayList<>();

    private void add(String name) {
        closing.add(name, () -> closed.add(name));
    }

    @Test
    void theLastPartMadeIsClosedFirst() {
        add("instance");
        add("tray");
        add("engine");

        closing.close();

        assertThat(closed).containsExactly("engine", "tray", "instance");
    }

    @Test
    void quitAndTheShutdownHookCloseOnce() {
        add("engine");

        closing.close();
        closing.close();

        assertThat(closed).containsExactly("engine");
    }

    @Test
    void aPartThatFailsToCloseDoesNotKeepTheOthersOpen() {
        add("instance");
        closing.add("tray", () -> {
            throw new IllegalStateException("the bus is gone");
        });
        add("engine");

        closing.close();

        assertThat(closed).containsExactly("engine", "instance");
    }

    @Test
    void aPartMadeAfterTheCloseIsClosedAtOnce() {
        closing.close();

        add("engine");

        assertThat(closed).containsExactly("engine");
    }
}
