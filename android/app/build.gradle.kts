import com.android.build.api.artifact.SingleArtifact
import java.util.Locale
import nl.enadeag.carchargeplanner.buildlogic.LicenceAssets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("ccp.no-google-libraries")
    // checkLegalFiles: android/legal names exactly the libraries and copied files the release build brings (#331)
    id("ccp.legal-files")
}

// the example settings file of the docs, which a JVM test and an instrumented test read through the engine
val exampleConfig: File = file("../../docs/example-engine.json")

// The languages of the texts: the plain values-<language> folders, such as values-nl, whose language is a real ISO 639
// one (so values-car, a car's UI mode, is none); region forms such as values-pt-rBR do not count.
fun languageFolders(res: File): List<String> = res.listFiles().orEmpty().map { it.name }
    .filter { it.startsWith("values-") && it.removePrefix("values-") in Locale.getISOLanguages() }
    .map { it.removePrefix("values-") }.sorted()

android {
    namespace = "nl.enadeag.carchargeplanner.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.enadeag.car_charge_planner"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-SNAPSHOT"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Only debug signing, AGP's own, until the release work brings the app's key; no git details in the package.
    buildTypes {
        all {
            vcsInfo.include = false
        }
        // en-XA (longer, accented texts) and ar-XB (right to left) to try the screens with; never in a release
        debug {
            isPseudoLocalesEnabled = true
        }
    }

    // The languages are the values-<code> folders and English, once: they decide the per-app language list
    // (the generated locales_config.xml, which the manifest gets by itself) and keep libraries' other languages out.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en") + languageFolders(file("src/main/res"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // the engine calls Stream.toList, URLEncoder.encode with a Charset and Duration.toSeconds, toSecondsPart and
        // toNanosPart, which Android 10 lacks (ADR 0010)
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG sets how much the app logs (LogcatServiceProvider)
        buildConfig = true
    }

    // Google's encrypted dependency list is of no use outside Play, and F-Droid refuses it
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        error += "NewApi"
        // a text missing or left over in a language, a placeholder that does not match, a plural without its forms
        error += listOf("MissingTranslation", "ExtraTranslation", "StringFormatMatches", "StringFormatInvalid",
            "StringFormatCount", "MissingQuantity", "UnusedQuantity", "ImpliedQuantity")
    }

    // SunnyDayPlan, the plan of a made-up sunny day, for the JVM tests and the instrumented tests alike
    sourceSets {
        getByName("test").kotlin.directories.add("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric gives the screens' tests the app's resources, as the build merges them
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
            // Robolectric's Android 16 (SDK 36) reaches into the JDK's file descriptors, which JDK 21 keeps closed
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            // The app's slf4j provider writes to android.util.Log, which the local tests' android.jar only stubs:
            // a call fails there, so the records of the engine and of the libraries go nowhere in these tests.
            it.systemProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider")
            // StringsResourcesTest reads the resources and the language list that the release build hands Android,
            // as the build makes it. AGP has no public handle on that file's folder, so the task's output is used.
            val localeConfig = files(tasks.named("generateReleaseLocaleConfig").map { task -> task.outputs.files })
            it.inputs.dir("src/main/res").withPropertyName("resources")
            it.inputs.files(localeConfig).withPropertyName("localeConfig")
            val example = exampleConfig.absolutePath
            it.inputs.file(example).withPropertyName("exampleConfig").withPathSensitivity(PathSensitivity.NONE)
            it.jvmArgumentProviders.add(CommandLineArgumentProvider {
                listOf("-Drelease.locale.config.dir=${localeConfig.singleFile}", "-Dexample.config=$example")
            })
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/** Puts the example settings file in the instrumented tests' assets, and nothing else of the docs. */
abstract class ExampleConfigAsset : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val example: RegularFileProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun copy() {
        example.get().asFile.copyTo(assets.get().file("example-engine.json").asFile, overwrite = true)
    }
}

val exampleConfigAsset = tasks.register<ExampleConfigAsset>("exampleConfigAsset") {
    example = exampleConfig
}

// the licence texts of the app and of its libraries, which the screen of the licences shows (#331)
val licenceAssets = tasks.register<LicenceAssets>("licenceAssets") {
    notice = file("../../NOTICE")
    licence = file("../../LICENSE")
    additionalTerms = file("../../ADDITIONAL-TERMS.md")
    legal = file("../legal")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(licenceAssets, LicenceAssets::getAssets)
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(exampleConfigAsset, ExampleConfigAsset::assets)
        // LicencesInTheApkTest reads the debug APK as the build packs it
        if (variant.name == "debug") {
            val apk = variant.artifacts.get(SingleArtifact.APK)
            tasks.withType<Test>().named { it == "testDebugUnitTest" }.configureEach {
                inputs.dir(apk).withPropertyName("apk")
                jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dapk.dir=${apk.get().asFile}") })
            }
        }
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // the engine's contract tests, with JUnit and AssertJ, for the Android implementations of its seams
    testImplementation(testFixtures(project(":engine")))
    // the rules on which app classes may open connections, write to Logcat or reach the engine (AppArchitectureTest)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinx.coroutines.test)
    // the screens' tests: JUnit 4 on Robolectric, run by the vintage engine beside the JUnit 5 tests
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    // Compose's test manifest, for the screens' tests; it puts an exported empty activity in the debug APK only
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX Test's runner, core and JUnit 4 rules, for the instrumented tests on a device or emulator
    // (the emulator job)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
}
