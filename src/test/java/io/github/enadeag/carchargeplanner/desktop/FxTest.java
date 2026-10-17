package io.github.enadeag.carchargeplanner.desktop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class that uses JavaFX through {@link Fx}: each of its tests starts without a window and fails when
 * it leaves an exception on the JavaFX thread, see {@link FxIsolation}. {@link Fx} refuses to run for a class without
 * the mark.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FxIsolation.class)
public @interface FxTest {
}
