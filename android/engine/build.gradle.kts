plugins {
    `java-library`
    `java-test-fixtures`
    id("ccp.maven-drift")
    id("ccp.no-google-libraries")
}

val engineSources = layout.projectDirectory.dir("../../engine/src/main/java")
val engineTests = layout.projectDirectory.dir("../../engine/src/test/java")

// The contract tests the Android implementations of the engine's seams extend, and the helpers they need (none yet):
// nothing else from the engine's tests, which stay in Maven. They are compiled here, against the JDK, because
// HttpGetContractTest's server (com.sun.net.httpserver) is not in the Android API that the app's own tests compile
// against; they run on the JVM, as the app's local tests do.
val contractTests = listOf(
    "nl/enadeag/carchargeplanner/engine/forecast/HttpGetContractTest.java",
    "nl/enadeag/carchargeplanner/engine/secrets/KeychainContractTest.java",
    "nl/enadeag/carchargeplanner/engine/store/FilePrivacyContractTest.java",
)
contractTests.filterNot { engineTests.file(it).asFile.isFile }.let { missing ->
    if (missing.isNotEmpty()) {
        throw GradleException("contract tests not found in engine/src/test/java: $missing")
    }
}

// The engine's own sources, compiled as they are: no copy, no published jar (ADR 0010).
sourceSets {
    main {
        java.setSrcDirs(listOf(engineSources))
        resources.setSrcDirs(emptyList<String>())
    }
    test {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
    testFixtures {
        java.setSrcDirs(listOf(engineTests))
        java.setIncludes(contractTests)
        resources.setSrcDirs(emptyList<String>())
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.check {
    dependsOn(tasks.named("compileTestFixturesJava"))
}

// the poms the engine's libraries come from: Gradle declares the same, with the catalog's versions
tasks.checkMavenDrift {
    parentPom = layout.projectDirectory.file("../../pom.xml")
    modulePom = layout.projectDirectory.file("../../engine/pom.xml")
}

dependencies {
    // api, as Maven's compile scope: the engine's methods take and give Jackson's types
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.core)
    api(libs.slf4j.api)

    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesApi(libs.junit.jupiter.params)
    testFixturesApi(libs.assertj.core)
    // for the tests that extend them, which run on the JUnit Platform
    testFixturesRuntimeOnly(libs.junit.jupiter.engine)
    testFixturesRuntimeOnly(libs.junit.platform.launcher)
}
