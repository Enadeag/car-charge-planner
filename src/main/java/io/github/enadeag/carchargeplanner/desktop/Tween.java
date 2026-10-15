package io.github.enadeag.carchargeplanner.desktop;

import java.util.function.DoubleConsumer;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;

/**
 * A short, eased change from old values to new ones, such as a chart's curve when another day shows. When the system
 * asks for reduced motion, the new values show at once.
 */
final class Tween {

    /** How long a change takes. */
    static final Duration LENGTH = Duration.millis(400);

    private final ObservableValue<Boolean> reducedMotion;
    private final DoubleConsumer frame;
    private final Transition transition;

    /**
     * Makes the tween; nothing moves until {@link #play()}.
     *
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); read at each play
     * @param frame draws one moment of the change, given how far along it is: from 0 (the old values) to 1 (the new
     *     ones); called on the JavaFX thread
     */
    Tween(ObservableValue<Boolean> reducedMotion, DoubleConsumer frame) {
        this.reducedMotion = reducedMotion;
        this.frame = frame;
        this.transition = new Transition() {
            {
                setCycleDuration(LENGTH);
                setInterpolator(Interpolator.EASE_OUT);
            }

            @Override
            protected void interpolate(double progress) {
                frame.accept(progress);
            }
        };
    }

    /** Starts the change from its beginning, stopping one still going on; under reduced motion it ends at once. */
    void play() {
        transition.stop();
        if (Boolean.TRUE.equals(reducedMotion.getValue())) {
            frame.accept(1);
        } else {
            transition.playFromStart();
        }
    }

    /**
     * Whether a change is going on.
     *
     * @return true while the values are on their way
     */
    boolean isRunning() {
        return transition.getStatus() == Transition.Status.RUNNING;
    }
}
