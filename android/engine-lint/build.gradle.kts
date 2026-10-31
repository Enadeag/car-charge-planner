// The engine's sources once more, as an Android library that nothing uses, only so that lint's NewApi checks them at
// the app's minSdk with core library desugaring: lint skips that check in a plain Java module such as :engine. The
// build fails on a call that Android 8 lacks and desugaring does not add (ADR 0010).
plugins {
    alias(libs.plugins.android.library)
    id("ccp.no-google-libraries")
}

android {
    namespace = "nl.enadeag.carchargeplanner.engine.lint"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        androidResources = false
    }

    sourceSets {
        named("main") {
            java.directories.add("../../engine/src/main/java")
        }
    }

    lint {
        checkOnly += "NewApi"
        error += "NewApi"
        abortOnError = true
    }
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.bundles.engine)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}

// Lint's NewApi reads the compiled classes, so they are compiled, but against the JDK 17 API: AGP compiles against
// android.jar's JDK image, which lacks calls that desugaring adds, such as Files.readString.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--release")
    options.compilerArgs.add("17")
}
