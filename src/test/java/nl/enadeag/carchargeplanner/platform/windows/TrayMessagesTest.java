package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The tray icon's messages without a real icon. */
class TrayMessagesTest {

    private final TrayMessages messages = new TrayMessages();
    private final List<String> shown = new ArrayList<>();
    private final AtomicInteger opened = new AtomicInteger();

    private void attached() {
        messages.attach((title, body) -> shown.add(title + " | " + body));
        messages.onClick(opened::incrementAndGet);
    }

    @Test
    void aMessageIsShownWithItsText() {
        attached();
        messages.show(new NotificationText("Charge window 10:00 – 15:00", "Set the current to 7 A"));

        assertThat(shown).containsExactly("Charge window 10:00 – 15:00 | Set the current to 7 A");
    }

    @Test
    void aClickOnTheMessageOpensTheDashboard() {
        attached();
        messages.show(new NotificationText("a", "b"));

        messages.claimClick();

        assertThat(opened).hasValue(1);
    }

    @Test
    void onceTheNotificationsAreClosedAClickOnAMessageStillShownDoesNothing() {
        attached();
        messages.show(new NotificationText("a", "b"));

        messages.stopPassingClicks();
        messages.claimClick();

        assertThat(opened).hasValue(0);
    }

    @Test
    void aClickWithNoMessageWaitingIsIgnoredAndAMessageCountsOnce() {
        attached();
        messages.claimClick(); // such as a double click on the icon
        messages.show(new NotificationText("a", "b"));
        messages.claimClick();
        messages.claimClick();

        assertThat(opened).hasValue(1);
    }

    @Test
    void withoutAnIconNothingIsShownAndTheLogSaysSoOnce() {
        var warnings = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(TrayMessages.class.getName());
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            messages.onClick(opened::incrementAndGet);
            for (int i = 0; i < 3; i++) {
                messages.show(new NotificationText("a", "b"));
            }
            messages.claimClick();
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(warnings).extracting(LogRecord::getMessage).containsExactly("Could not show a notification: there is no tray icon");
        assertThat(opened).hasValue(0);
    }

    @Test
    void afterTheIconLeftTheTrayNoMessageIsShownAndAnOldOneNoLongerOpens() {
        attached();
        messages.show(new NotificationText("a", "b"));
        messages.detach();

        messages.show(new NotificationText("c", "d"));
        messages.claimClick();

        assertThat(shown).hasSize(1);
        assertThat(opened).hasValue(0);
    }
}
