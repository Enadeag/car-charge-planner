package nl.enadeag.carchargeplanner.android

import java.io.File
import java.net.URL
import java.util.Properties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.robolectric.internal.dependency.DependencyJar
import org.robolectric.internal.dependency.DependencyResolver
import org.robolectric.plugins.DefaultSdkProvider

/**
 * The jars of Robolectric's Android that the build hands the tests (`robolectricSdkJars` in `app/build.gradle.kts`,
 * given here as `robolectric.sdk.jars`), held to the ones this Robolectric asks for: the tests run with Robolectric
 * offline, so after a Robolectric update the build must name the jars the new version wants (#484).
 */
class RobolectricSdkJarsTest {

    @TempDir
    lateinit var folder: File

    /** The jars the build declares, `group:name:version` by SDK. */
    private val declared: Map<Int, String> = System.getProperty("robolectric.sdk.jars").split(",").associate {
        val (sdk, jar) = it.split("=", limit = 2)
        sdk.toInt() to jar
    }

    /** The jar Robolectric's own SDK provider asks for on [sdk], as `group:name:version`. */
    private fun askedFor(sdk: Int): String {
        var asked: DependencyJar? = null
        val file = File(folder, "android-all.jar").apply { createNewFile() }
        val resolver = DependencyResolver { jar: DependencyJar ->
            asked = jar
            file.toURI().toURL() as URL
        }
        DefaultSdkProvider(resolver).sdks.single { it.apiLevel == sdk }.jarPath
        return asked!!.let { "${it.groupId}:${it.artifactId}:${it.version}" }
    }

    @Test
    fun theBuildDeclaresTheJarsThisRobolectricAsksFor() {
        assertThat(declared).isNotEmpty()
        for ((sdk, jar) in declared) {
            assertThat(jar).`as`("the jar of SDK %s", sdk).isEqualTo(askedFor(sdk))
        }
    }

    @Test
    fun theSdkTheTestsRunOnByDefaultIsDeclared() {
        val properties = Properties()
        javaClass.getResourceAsStream("/robolectric.properties").use { properties.load(it) }

        assertThat(declared).containsKey(properties.getProperty("sdk").toInt())
    }
}
