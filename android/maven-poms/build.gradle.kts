// What the Android build reads from the Maven poms: the versions the settings of android/ and of build-logic put in
// their catalog, and the engine's libraries for checkMavenDrift. A build of its own, without a catalog, as the
// catalog's versions come from it; its tests are in build-logic.
plugins {
    `java-gradle-plugin`
}

group = "nl.enadeag.carchargeplanner"

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

gradlePlugin {
    plugins {
        create("mavenVersions") {
            id = "ccp.maven-versions"
            implementationClass = "nl.enadeag.carchargeplanner.buildlogic.MavenVersionsPlugin"
        }
    }
}
