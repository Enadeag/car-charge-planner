plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.pitest) apply false
    // puts build-logic's classes where every module's build file sees them (engine-lint uses AndroidApiClassesCheck)
    id("ccp.maven-drift") apply false
}

// the tests of the build's own checks run with every check
tasks.check {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
