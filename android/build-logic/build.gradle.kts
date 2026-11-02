// The checks the Android build runs on itself, as plugins, with the tests that show they fail when they should.
plugins {
    `java-gradle-plugin`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("mavenDrift") {
            id = "ccp.maven-drift"
            implementationClass = "nl.enadeag.carchargeplanner.buildlogic.MavenDriftPlugin"
        }
        create("noGoogleLibraries") {
            id = "ccp.no-google-libraries"
            implementationClass = "nl.enadeag.carchargeplanner.buildlogic.NoGoogleLibrariesPlugin"
        }
    }
}
