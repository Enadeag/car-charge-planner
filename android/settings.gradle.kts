pluginManagement {
    includeBuild("maven-poms")
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("ccp.maven-versions")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
    // gradle/libs.versions.toml, which Gradle reads into libs by itself, and the versions of Jackson, slf4j, JUnit,
    // AssertJ and ArchUnit from the Maven build's pom.xml
    versionCatalogs {
        create("libs") {
            mavenVersions.addTo(this, file("../pom.xml"))
        }
    }
}

rootProject.name = "car-charge-planner-android"

include(":engine", ":engine-lint", ":app")
