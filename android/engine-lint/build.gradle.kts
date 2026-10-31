import nl.enadeag.carchargeplanner.buildlogic.AndroidApiClassesCheck

// The engine's sources once more, as an Android library that nothing uses, for two checks at the app's minSdk with
// core library desugaring (ADR 0010). Lint's NewApi fails on a class or method that some Android version has but
// Android 8 lacks and desugaring does not add; lint skips that check in a plain Java module such as :engine.
// checkAndroidApiClasses fails on a JDK class that no Android version has, which NewApi does not see.
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

// Lint needs the module's classes compiled, and so does checkAndroidApiClasses. They are compiled against the JDK 17
// API: AGP compiles against android.jar's JDK image, which lacks calls that desugaring adds, such as Files.readString.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--release")
    options.compilerArgs.add("17")
}

val checkAndroidApiClasses = tasks.register<AndroidApiClassesCheck>("checkAndroidApiClasses") {
    classes.from(tasks.named<JavaCompile>("compileDebugJavaWithJavac").flatMap { it.destinationDirectory })
    androidApi.from(androidComponents.sdkComponents.bootClasspath)
}

tasks.check {
    dependsOn(checkAndroidApiClasses)
}
