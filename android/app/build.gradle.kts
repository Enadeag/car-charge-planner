import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("ccp.no-google-libraries")
}

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
        localeFilters += listOf("en") + file("src/main/res").listFiles().orEmpty()
            .map { it.name }.filter { it.matches(Regex("values-[a-z]{2,3}")) }.map { it.removePrefix("values-") }.sorted()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // the engine uses java.time and java.nio.file, which Android 8 lacks in part (ADR 0010)
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // The app's slf4j provider writes to android.util.Log, which the local tests' android.jar only stubs:
            // a call fails there, so the records of the engine and of the libraries go nowhere in these tests.
            it.systemProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider")
        }
    }
}

// StringsResourcesTest reads the resources, so a changed text runs it again, and the language list that the release
// build hands Android, as the build makes it
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/res").withPropertyName("resources")
    dependsOn("generateReleaseLocaleConfig")
    systemProperty("release.locale.config",
        layout.buildDirectory.file("generated/res/localeConfig/release/xml/_generated_res_locale_config.xml")
            .get().asFile.path)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // the engine's contract tests, with JUnit and AssertJ, for the Android implementations of its seams
    testImplementation(testFixtures(project(":engine")))
    // the rules on which app classes may open connections or write to Logcat (AppArchitectureTest)
    testImplementation(libs.archunit)

    // AndroidX Test's runner, core and JUnit 4 rules, for the instrumented tests on a device or emulator
    // (the emulator job)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
}
