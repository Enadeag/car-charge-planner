package nl.enadeag.carchargeplanner.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * Adds {@code mavenVersions}, a {@link MavenVersions}, to the settings, for the Android build and for its
 * {@code build-logic} alike, which both fill their {@code libs} catalog with the Maven build's versions.
 */
public final class MavenVersionsPlugin implements Plugin<Settings> {

    /** Makes the plugin; Gradle calls it. */
    public MavenVersionsPlugin() {
    }

    @Override
    public void apply(Settings settings) {
        settings.getExtensions().create("mavenVersions", MavenVersions.class);
    }
}
