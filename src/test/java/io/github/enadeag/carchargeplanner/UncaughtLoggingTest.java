package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UncaughtLoggingTest {

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Logger logger;
    private UncaughtLogging handler;

    @BeforeEach
    void capture() {
        logger = Logger.getLogger("uncaught-test-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(capture);
        handler = new UncaughtLogging(System.getLogger(logger.getName()));
    }

    @AfterEach
    void release() {
        logger.removeHandler(capture);
    }

    @Test
    void whatAVirtualThreadThrowsIsLoggedAsSevereWithTheException() throws InterruptedException {
        var thrown = new IllegalStateException("broken");

        Thread.ofVirtual().uncaughtExceptionHandler(handler).start(() -> {
            throw thrown;
        }).join();

        assertThat(logged).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
            assertThat(record.getThrown()).isSameAs(thrown);
            assertThat(record.getMessage()).contains("VirtualThread");
        });
    }

    @Test
    void asTheDefaultItLogsForEveryThreadWithoutAHandlerOfItsOwn() throws InterruptedException {
        var before = Thread.getDefaultUncaughtExceptionHandler();
        try {
            handler.install();
            Thread.ofPlatform().name("planner").start(() -> {
                throw new IllegalStateException("broken");
            }).join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }

        assertThat(logged).singleElement().extracting(LogRecord::getMessage).asString().contains("\"planner\"");
    }

    @Test
    void itNeedsALogger() {
        assertThatThrownBy(() -> new UncaughtLogging(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
