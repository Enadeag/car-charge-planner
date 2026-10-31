import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("ccp.no-google-libraries")
}

android {
    namespace = "nl.enadeag.carchargeplanner.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.enadeag.car_charge_planner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-SNAPSHOT"
    }

    // Only debug signing, AGP's own, until the release work brings the app's key; no git details in the package.
    buildTypes {
        all {
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // the engine uses java.time and java.nio.file, which Android 8 lacks in part (ADR 0010)
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    // Google's encrypted dependency list is of no use outside Play, and F-Droid refuses it
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        error += "NewApi"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
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
}
