package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Collects what JavaFX's CSS engine warns about, such as a colour token it cannot resolve, while it is open. */
public final class CssWarnings extends Handler implements AutoCloseable {

    private final Logger log = Logger.getLogger("javafx.css");
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    public CssWarnings() {
        log.addHandler(this);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public void publish(LogRecord entry) {
        warnings.add(entry.getMessage());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        log.removeHandler(this);
    }
}
