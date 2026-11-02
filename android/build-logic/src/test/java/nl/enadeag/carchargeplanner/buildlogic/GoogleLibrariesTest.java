package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleLibrariesTest {

    @ParameterizedTest
    @ValueSource(strings = {"com.google.android.gms:play-services-location:21.3.0",
        "com.google.firebase:firebase-messaging:25.0.0", "com.google.android.play:review:2.0.2",
        "com.google.android.play.core:core:1.10.3"})
    void aLibraryOfARefusedGroupIsFoundWithEveryConfigurationItIsIn(String module) {
        var found = GoogleLibraries.findIn(Map.of(
                "releaseRuntimeClasspath", List.of("androidx.core:core:1.16.0", module),
                "debugRuntimeClasspath", List.of(module)));

        assertThat(found).containsExactly(module + " in debugRuntimeClasspath, releaseRuntimeClasspath");
    }

    @Test
    void googlesOpenSourceLibrariesAndAndroidXAreAllowed() {
        var found = GoogleLibraries.findIn(Map.of("releaseRuntimeClasspath", List.of(
                "com.google.guava:guava:33.4.0-android", "com.google.android.material:material:1.13.0",
                "com.google.android.gmsx:other:1.0", "androidx.compose.ui:ui:1.11.4")));

        assertThat(found).isEmpty();
    }
}
