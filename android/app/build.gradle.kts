import com.android.build.api.artifact.SingleArtifact
import java.util.Locale
import nl.enadeag.carchargeplanner.buildlogic.LicenceAssets
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("ccp.no-google-libraries")
    // checkLegalFiles: android/legal names exactly the libraries and copied files the release build brings (#331)
    id("ccp.legal-files")
    alias(libs.plugins.pitest)
}

// the example settings file of the docs, which a JVM test and an instrumented test read through the engine
val exampleConfig: File = file("../../docs/example-engine.json")

// What the logic tests need of their JVM, for the unit-test task and for PIT's test JVMs alike. The app's slf4j provider
// writes to android.util.Log, which the local tests' android.jar only stubs: a call fails there, so the records of the
// engine and of the libraries go nowhere in these tests.
val logicTestProperties = mapOf("slf4j.provider" to "org.slf4j.helpers.NOP_FallbackServiceProvider")

// The languages of the texts: the plain values-<language> folders, such as values-nl, whose language is a real ISO 639
// one (so values-car, a car's UI mode, is none); region forms such as values-pt-rBR do not count.
fun languageFolders(res: File): List<String> = res.listFiles().orEmpty().map { it.name }
    .filter { it.startsWith("values-") && it.removePrefix("values-") in Locale.getISOLanguages() }
    .map { it.removePrefix("values-") }.sorted()

// The short commit of a CI build (BUILD_COMMIT, which android.yml sets to a pull request's head commit or the pushed
// commit), else "dev"; read as a provider, so the configuration cache sees the variable. An empty value counts as
// unset.
val debugBuildLabel: Provider<String> = providers.environmentVariable("BUILD_COMMIT")
    .filter { it.isNotEmpty() }.map { it.take(7) }.orElse("dev")

android {
    namespace = "nl.enadeag.carchargeplanner.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.enadeag.car_charge_planner"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-SNAPSHOT"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The debug build is signed with the project's public debug key (not secret: anyone can sign with it, so it proves
    // nothing about the owner); release signing is #271/#272's. checkReleaseNotDebugSigned keeps the debug key off
    // every build type other than debug. No git details in the package.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        all {
            vcsInfo.include = false
        }
        // en-XA (longer, accented texts) and ar-XB (right to left) to try the screens with; never in a release.
        // An id of its own, so the debug build sits beside a release build on one phone, and a version name that shows
        // the commit on CI (BUILD_COMMIT) and "dev" elsewhere.
        debug {
            isPseudoLocalesEnabled = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+" + debugBuildLabel.get()
        }
    }

    // The languages are the values-<code> folders and English, once: they decide the per-app language list
    // (the generated locales_config.xml, which the manifest gets by itself) and keep libraries' other languages out.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en") + languageFolders(file("src/main/res"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // the engine calls Stream.toList, URLEncoder.encode with a Charset and Duration.toSeconds, toSecondsPart and
        // toNanosPart, which Android 10 lacks (ADR 0010)
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG sets how much the app logs (LogcatServiceProvider)
        buildConfig = true
    }

    // Google's encrypted dependency list is of no use outside Play, and F-Droid refuses it
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        error += "NewApi"
        // a text missing or left over in a language, a placeholder that does not match, a plural without its forms
        error += listOf("MissingTranslation", "ExtraTranslation", "StringFormatMatches", "StringFormatInvalid",
            "StringFormatCount", "MissingQuantity", "UnusedQuantity", "ImpliedQuantity")
    }

    // SunnyDayPlan, the plan of a made-up sunny day, for the JVM tests and the instrumented tests alike
    sourceSets {
        getByName("test").kotlin.directories.add("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/kotlin")
    }

    testOptions {
        // Robolectric gives the screens' tests the app's resources, as the build merges them
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
            // Robolectric's Android 17 (SDK 37) reaches into the JDK's file descriptors, which JDK 21 keeps closed
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            it.systemProperties(logicTestProperties)
            // StringsResourcesTest reads the resources and the language list that the release build hands Android,
            // as the build makes it. AGP has no public handle on that file's folder, so the task's output is used.
            val localeConfig = files(tasks.named("generateReleaseLocaleConfig").map { task -> task.outputs.files })
            it.inputs.dir("src/main/res").withPropertyName("resources")
            it.inputs.files(localeConfig).withPropertyName("localeConfig")
            val example = exampleConfig.absolutePath
            it.inputs.file(example).withPropertyName("exampleConfig").withPathSensitivity(PathSensitivity.NONE)
            it.jvmArgumentProviders.add(CommandLineArgumentProvider {
                listOf("-Drelease.locale.config.dir=${localeConfig.singleFile}", "-Dexample.config=$example")
            })
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/** Puts the example settings file in the instrumented tests' assets, and nothing else of the docs. */
abstract class ExampleConfigAsset : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val example: RegularFileProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun copy() {
        example.get().asFile.copyTo(assets.get().file("example-engine.json").asFile, overwrite = true)
    }
}

val exampleConfigAsset = tasks.register<ExampleConfigAsset>("exampleConfigAsset") {
    example = exampleConfig
}

// the licence texts of the app and of its libraries, which the screen of the licences shows (#331)
val licenceAssets = tasks.register<LicenceAssets>("licenceAssets") {
    notice = file("../../NOTICE")
    licence = file("../../LICENSE")
    additionalTerms = file("../../ADDITIONAL-TERMS.md")
    legal = file("../legal")
}

/**
 * Fails when a build type other than debug (release, or any later one) is signed with the debug key: by its effective
 * signing config being the debug one (the build type's own, else the first product flavor's that sets one, else the
 * default config's), or by a keystore file whose bytes are those of debug.keystore. It does not see a
 * re-exported copy of the key with other bytes, nor signing set after the DSL is final (the Variant API,
 * `android.injected.signing.*`). It has no outputs, so it runs every time.
 */
abstract class ReleaseSigningCheck : DefaultTask() {

    /** The signing config's name by build type; a build type without one is left out. */
    @get:Input
    abstract val signingNames: MapProperty<String, String>

    /** The keystore files of the non-debug build types' signing configs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keystores: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val debugKeystore: RegularFileProperty

    @TaskAction
    fun check() {
        val debug = debugKeystore.get().asFile.readBytes()
        val byName = signingNames.get().filterValues { it == "debug" }.keys
        val byBytes = keystores.files.filter { it.isFile && it.readBytes().contentEquals(debug) }
        if (byName.isNotEmpty() || byBytes.isNotEmpty()) {
            throw GradleException("A build type other than debug is signed with the debug key (signing config " +
                "'debug' on $byName, keystore copies of it: $byBytes): give it a key of its own, not the debug key")
        }
    }
}

val checkReleaseNotDebugSigned = tasks.register<ReleaseSigningCheck>("checkReleaseNotDebugSigned") {
    debugKeystore = layout.projectDirectory.file("debug.keystore")
}
tasks.named("check") { dependsOn(checkReleaseNotDebugSigned) }

androidComponents {
    finalizeDsl { dsl ->
        // the signing config a build type ends up with: its own, else the first product flavor's that sets one (in the
        // order of the flavor dimensions), else the default config's
        val dimensions = dsl.flavorDimensions
        val flavors = dsl.productFlavors.sortedBy { dimensions.indexOf(it.dimension) }
        val signed = dsl.buildTypes.filter { it.name != "debug" }.mapNotNull { type ->
            val effective = type.signingConfig ?: flavors.firstNotNullOfOrNull { it.signingConfig }
                ?: dsl.defaultConfig.signingConfig
            effective?.let { type.name to it }
        }
        checkReleaseNotDebugSigned.configure {
            signingNames.putAll(signed.associate { (type, signing) -> type to signing.name })
            keystores.from(signed.mapNotNull { (_, signing) -> signing.storeFile })
        }
    }
    onVariants { variant ->
        // every build of a build type other than debug (assembleRelease, bundleRelease) needs the guard, not only check
        if (variant.buildType != "debug") {
            val preBuild = "pre" + variant.name.replaceFirstChar { it.uppercase() } + "Build"
            tasks.matching { it.name == preBuild }.configureEach { dependsOn(checkReleaseNotDebugSigned) }
        }
        variant.sources.assets?.addGeneratedSourceDirectory(licenceAssets, LicenceAssets::getAssets)
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(exampleConfigAsset, ExampleConfigAsset::assets)
        // MergedManifestTest reads the release build's manifest, with what the libraries add to it
        if (variant.name == "release") {
            val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            tasks.withType<Test>().named { it == "testDebugUnitTest" }.configureEach {
                inputs.file(manifest).withPropertyName("releaseManifest")
                jvmArgumentProviders.add(CommandLineArgumentProvider {
                    listOf("-Drelease.manifest=${manifest.get().asFile}")
                })
            }
        }
        // LicencesInTheApkTest reads the debug APK as the build packs it
        if (variant.name == "debug") {
            val apk = variant.artifacts.get(SingleArtifact.APK)
            tasks.withType<Test>().named { it == "testDebugUnitTest" }.configureEach {
                inputs.dir(apk).withPropertyName("apk")
                jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dapk.dir=${apk.get().asFile}") })
            }
        }
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // the engine's contract tests, with JUnit and AssertJ, for the Android implementations of its seams
    testImplementation(testFixtures(project(":engine")))
    // the rules on which app classes may open connections, write to Logcat or reach the engine (AppArchitectureTest)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinx.coroutines.test)
    // the screens' tests: JUnit 4 on Robolectric, run by the vintage engine beside the JUnit 5 tests
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.compose.ui.test.junit4)
    // above the 3.5.0 that Compose's test rule brings: on Android 17 (SDK 37) that one looks up
    // InputManager.getInstance(), which Android 17 removed, and the screen tests fail (#321)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.work.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    // PIT's classpath (the fork's) leaves out two things the unit-test task has: the JUnit 5 engine, which the engine's
    // test fixtures bring at run time (without it PIT finds no JUnit 5 tests), and the app's Java resources (without
    // slf4j's service file LogcatLoggingTest.slf4jFindsTheProvider fails), which it looks for where older AGP versions
    // put them
    pitestRuntimeOnly(platform(libs.junit.bom))
    pitestRuntimeOnly(libs.junit.jupiter.engine)
    pitestRuntimeOnly(files(layout.projectDirectory.dir("src/main/resources")))
    // Compose's test manifest, for the screens' tests; it puts an exported empty activity in the debug APK only
    debugImplementation(libs.compose.ui.test.manifest)

    // AndroidX Test's runner, core and JUnit 4 rules, for the instrumented tests on a device or emulator
    // (the emulator job)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
}

// The app's logic, which PIT mutates (CONTRIBUTING.md, What is tested where): the classes with plain JUnit 5 tests.
// The screens, tested on Robolectric only, are left out, and so are the notifications' words and their posting
// (ui.notify) and the background work's alarm, WorkManager work and receivers; their tests keep hand mutants in the
// review.
val pitLogic = listOf("form.*", "setup.*", "settings.*", "store.*", "home.*", "start.*", "licences.*", "logging.*",
    "secrets.*", "notify.*", "prefs.*", "OwnCode*", "MainThreadViolations*", "AndroidHttpGet*", "AndroidPlatform*",
    // the background work's rules, its rounds, its schedule and the engine's access that tells it of a change
    "background.BackgroundRules*", "background.BackgroundRounds*", "background.BackgroundSchedule*",
    "background.ReschedulingAccess*",
    // ChartGeometry.kt: the charts' geometry and the shapes they draw, apart from the Compose code in DayCharts.kt
    "ui.home.ChartGeometryKt*", "ui.home.PlotArea*", "ui.home.CurvePoint*", "ui.home.PlacedTick*",
    "ui.home.TimeAxis*", "ui.home.Point*", "ui.home.Shape*", "ui.home.SolarDrawing*", "ui.home.BatteryBarLayout*",
    // the view model (AppScreen.kt, apart from the screen's Compose code) and the source colours
    "ui.AppViewModel*", "ui.SourceColours*")
    .map { "nl.enadeag.carchargeplanner.android.$it" }.toSet()

// ./gradlew :app:pitestDebug, or with -PpitClasses=<comma-separated class names or globs> for the changed classes only
val pitClasses: Set<String>? = providers.gradleProperty("pitClasses").orNull?.let { value ->
    val names = value.split(",").map(String::trim).filter(String::isNotEmpty).toSet()
    if (names.isEmpty()) throw GradleException("-PpitClasses names no class: give class names or globs, comma-separated")
    names
}

pitest {
    pitestVersion = libs.versions.pitest.asProvider()
    junit5PluginVersion = libs.versions.pitest.junit5
    // as the Maven runs: two test JVMs at once, each with a heap of 512 MB, and PIT's own JVM with as much
    threads = 2
    jvmArgs = listOf("-Xmx512m") + logicTestProperties.map { (name, value) -> "-D$name=$value" }
    mainProcessJvmArgs = listOf("-Xmx512m")
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    targetClasses = pitClasses ?: pitLogic
    // The floor is checkPitScore's, which leaves the Kotlin compiler's own mutants out; PIT's own share counts them
    mutationThreshold = 0
    targetTests = setOf("nl.enadeag.carchargeplanner.android.*")
    excludedTestClasses = listOf(
        // the screens' tests, JUnit 4 on Robolectric, which runs Android in the test JVM
        "AppOnRobolectricTest", "AppWiringTest", "MainActivity*Test", "licences.AssetLicenceFilesTest", "ui.*Screen*Test",
        "ui.AppThemeTest", "ui.BarScaffoldTest", "ui.home.DashboardWordsTest", "ui.home.TripAdviceWordsTest",
        "ui.home.DrawnShapesTest",
        "ui.notify.NoticeWordsTest", "ui.notify.NotificationsTest", "background.AndroidNoticeAlarmTest",
        "background.WorkManagerRoundsTest", "background.RoundWorkerTest", "background.BackgroundReceiversTest",
        "background.BackgroundLifecycleTest",
        // what the build makes rather than what the code does: the resources, the manifest, the APK and the engine on
        // the example settings file; and the ArchUnit rules, which read the classes' structure and kill no mutant
        "StringsResourcesTest", "DataExtractionRulesTest", "ManifestTest", "MergedManifestTest",
        "NetworkSecurityConfigTest", "licences.LicencesInTheApkTest", "AppEngineTest", "AppArchitectureTest",
    ).map { "nl.enadeag.carchargeplanner.android.$it" }.toSet()
}

/**
 * The share of mutants killed, as PIT's report has it but without the mutants of code the Kotlin compiler adds, which
 * no test can kill (and no PIT filter can drop without also dropping the real mutants on the same line): the removed
 * calls of `Intrinsics` (the null checks) and `ResultKt.throwOnFailure`, a coroutine's or a lambda's result replaced
 * by null (`invokeSuspend`, a suspend function's `Object` and a lambda's `Unit`), and the logger's `getLOG`. It fails
 * below [threshold]; a threshold of 0 only prints the share.
 */
abstract class PitScore : DefaultTask() {

    @get:InputFile
    abstract val mutations: RegularFileProperty

    @get:Input
    abstract val threshold: Property<Int>

    @TaskAction
    fun check() {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
        val all = factory.newDocumentBuilder().parse(mutations.get().asFile).documentElement.childNodes
        var counted = 0
        var killed = 0
        for (i in 0 until all.length) {
            val mutation = all.item(i) as? org.w3c.dom.Element ?: continue
            fun text(name: String) = mutation.getElementsByTagName(name).item(0).textContent
            val description = text("description")
            val method = text("mutatedMethod")
            val result = text("methodDescription")
            val compilers = description.startsWith("removed call to kotlin/jvm/internal/Intrinsics::")
                || description.startsWith("removed call to kotlin/ResultKt::")
                || description.startsWith("replaced return value with null") && (method == "invokeSuspend"
                    || result.endsWith("Lkotlin/coroutines/Continuation;)Ljava/lang/Object;")
                    || result.endsWith(")Lkotlin/Unit;"))
                || method == "getLOG"
            if (compilers) continue
            counted++
            if (mutation.getAttribute("status") in setOf("KILLED", "TIMED_OUT")) killed++
        }
        val share = if (counted == 0) 0.0 else 100.0 * killed / counted
        logger.lifecycle("PIT without the Kotlin compiler's mutants: $killed of $counted killed (%.1f %%)".format(share))
        if (share < threshold.get()) {
            throw GradleException("PIT killed %.1f %% of the mutants, below the floor of ${threshold.get()} %%".format(share))
        }
    }
}

// the share of mutants killed without the compiler's own, a few points below the 88 % it was when set, for the full run
// only: a run narrowed to some classes may be small or hold many survivors
val checkPitScore = tasks.register<PitScore>("checkPitScore") {
    mutations.set(layout.buildDirectory.file("reports/pitest/debug/mutations.xml"))
    threshold.set(if (pitClasses == null) 85 else 0)
}

// the Android PIT plugin adds the variants' tasks after this script has run
tasks.matching { it.name == "pitestDebug" }.configureEach { finalizedBy(checkPitScore) }
