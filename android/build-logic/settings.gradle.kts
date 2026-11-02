// The pom reader, whose plugin fills the catalog below: a build of its own, as this one cannot fill its own catalog.
pluginManagement {
    includeBuild("../maven-poms")
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

// the pom reader once more, as a library: checkMavenDrift reads the engine's pom with it
includeBuild("../maven-poms")
