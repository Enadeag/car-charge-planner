// The pom reader, whose plugin fills the catalog below: a build of its own, as this one cannot fill its own catalog.
// Included here for the settings plugin, and plainly at the end for the library that checkMavenDrift uses: the
// substitution of a dependency on it works only for a build included outside pluginManagement.
pluginManagement {
    includeBuild("../maven-poms")
    // no default Gradle Plugin Portal: the plugins here are Gradle's own or come from the included build
    repositories {
        mavenCentral()
    }
}

plugins {
    id("ccp.maven-versions")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
            mavenVersions.addTo(this, file("../../pom.xml"))
        }
    }
}

rootProject.name = "build-logic"

// the pom reader once more, as a library: checkMavenDrift reads the engine's pom with it (see pluginManagement above)
includeBuild("../maven-poms")
