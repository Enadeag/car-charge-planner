package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegalFilesTest {

    private static final String ICONS = "android/app/src/main/kotlin/Icons.kt";

    private static final String APACHE = """
            Apache License 2.0
            Artifact: androidx.core:core
            Artifact: org.jetbrains.kotlin:kotlin-stdlib

            Copied: two icons, in
            android/app/src/main/kotlin/Icons.kt
            From: material-icons-extended-android 1.7.8
            Licence: Apache-2.0.

                                 Apache License
            """;

    private static final String JACKSON = """
            Jackson
            Artifact: tools.jackson.core:jackson-core

            ==== META-INF/NOTICE of jackson-core ====
            Jackson is a JSON processor.
            ==== META-INF/LICENSE of jackson-core, the same in jackson-databind ====
            """;

    private static final Map<String, List<String>> USED = Map.of(
            "releaseRuntimeClasspath", List.of("androidx.core:core", "org.jetbrains.kotlin:kotlin-stdlib",
                    "tools.jackson.core:jackson-core"));

    private static final Map<String, List<String>> NOTICES = Map.of(
            "tools.jackson.core:jackson-core", List.of("META-INF/NOTICE"));

    private static List<String> problems(Map<String, ? extends Collection<String>> used,
            Map<String, ? extends Collection<String>> notices, Map<String, String> legal, Collection<String> headers,
            Set<String> files) {
        return LegalFiles.problems(used, notices, legal, headers, files::contains);
    }

    private static List<String> problems(Map<String, String> legal) {
        return problems(USED, NOTICES, legal, List.of(ICONS), Set.of(ICONS));
    }

    @Test
    void filesThatNameExactlyWhatTheAppBringsPass() {
        assertThat(problems(Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON))).isEmpty();
    }

    @Test
    void aLibraryNoFileNamesFailsWithItsConfigurationAndTheFix() {
        var used = Map.of("releaseRuntimeClasspath", List.of("androidx.core:core", "org.jetbrains.kotlin:kotlin-stdlib",
                        "tools.jackson.core:jackson-core", "androidx.window:window"),
                "coreLibraryDesugaring", List.of("com.android.tools:desugar_jdk_libs_nio"));

        assertThat(problems(used, NOTICES, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON), List.of(ICONS),
                Set.of(ICONS))).containsExactly(
                "the release build has `androidx.window:window` (via `releaseRuntimeClasspath`) but no file in "
                        + "android/legal names it: add an `Artifact:` line to the file of its licence (see CONTRIBUTING)",
                "the release build has `com.android.tools:desugar_jdk_libs_nio` (via `coreLibraryDesugaring`) but no "
                        + "file in android/legal names it: add an `Artifact:` line to the file of its licence (see "
                        + "CONTRIBUTING)");
    }

    @Test
    void aLineForALibraryTheAppNoLongerUsesFails() {
        var apache = APACHE.replace("Artifact: androidx.core:core\n",
                "Artifact: androidx.core:core\nArtifact: androidx.print:print\n");

        assertThat(problems(Map.of("apache-2.0.txt", apache, "jackson.txt", JACKSON))).containsExactly(
                "android/legal/apache-2.0.txt names `androidx.print:print`, which the app no longer uses: remove the "
                        + "line");
    }

    @Test
    void aLibraryNamedInTwoFilesFails() {
        var jackson = JACKSON.replace("Jackson\n", "Jackson\nArtifact: androidx.core:core\n");

        assertThat(problems(Map.of("apache-2.0.txt", APACHE, "jackson.txt", jackson))).containsExactly(
                "`androidx.core:core` is named in android/legal/apache-2.0.txt and android/legal/jackson.txt: keep "
                        + "one `Artifact:` line, in the file of its licence");
    }

    @Test
    void anotherVersionOfALibraryPassesAsTheFilesNameNoVersions() {
        // the libraries come without versions; a bump changes nothing of what is compared
        var bumped = Map.of("releaseRuntimeClasspath", List.of("tools.jackson.core:jackson-core",
                "org.jetbrains.kotlin:kotlin-stdlib", "androidx.core:core"));

        assertThat(problems(bumped, NOTICES, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON),
                List.of(ICONS), Set.of(ICONS))).isEmpty();
    }

    @Test
    void anArtifactLineMustBeALineOfItsOwn() {
        var apache = APACHE.replace("Artifact: androidx.core:core\n", "See Artifact: androidx.core:core\n");

        assertThat(problems(Map.of("apache-2.0.txt", apache, "jackson.txt", JACKSON))).singleElement()
                .asString().startsWith("the release build has `androidx.core:core`");
    }

    @Test
    void aCopiedFileThatDoesNotExistFails() {
        assertThat(problems(USED, NOTICES, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON), List.of(),
                Set.of())).containsExactly(
                "android/legal/apache-2.0.txt names `" + ICONS + "` in a `Copied:` block, but there is no such file: "
                        + "correct the path, or remove the block when the file is gone");
    }

    @Test
    void aSourceWithAThirdPartysHeaderInNoCopiedBlockFails() {
        var other = "android/app/src/main/kotlin/Other.kt";

        assertThat(problems(USED, NOTICES, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON),
                List.of(other, ICONS), Set.of(ICONS, other))).containsExactly(
                "`" + other + "` has a third party's header but is in no `Copied:` block: name it in the `Copied:` "
                        + "block of the file of its licence in android/legal (see CONTRIBUTING)");
    }

    @Test
    void aPathOutsideACopiedBlockDoesNotCount() {
        var outside = APACHE.replace("Copied: two icons, in\n", "Two icons, in\n");

        assertThat(problems(Map.of("apache-2.0.txt", outside, "jackson.txt", JACKSON))).containsExactly(
                "`" + ICONS + "` has a third party's header but is in no `Copied:` block: name it in the `Copied:` "
                        + "block of the file of its licence in android/legal (see CONTRIBUTING)");
    }

    @Test
    void aCopiedBlockWithoutFromOrWithoutAFileFails() {
        var withoutFrom = APACHE.replace("From: material-icons-extended-android 1.7.8\n", "");
        var withoutFile = APACHE.replace(ICONS + "\n", "");

        assertThat(problems(USED, NOTICES, Map.of("apache-2.0.txt", withoutFrom, "jackson.txt", JACKSON), List.of(),
                Set.of())).containsExactly("android/legal/apache-2.0.txt has a `Copied:` block without a `From:` "
                        + "line: say where the copied file comes from");
        assertThat(problems(USED, NOTICES, Map.of("apache-2.0.txt", withoutFile, "jackson.txt", JACKSON), List.of(),
                Set.of())).containsExactly("android/legal/apache-2.0.txt has a `Copied:` block that names no file: "
                        + "put the copied file's path, from the repository's root, on a line of its own");
    }

    @Test
    void aJarsNoticeThatTheFileDoesNotCarryFails() {
        var notices = Map.of("tools.jackson.core:jackson-core", List.of("META-INF/NOTICE", "META-INF/NOTICE.txt"),
                "androidx.core:core", List.of("META-INF/NOTICE.md"));

        assertThat(problems(USED, notices, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON), List.of(ICONS),
                Set.of(ICONS))).containsExactly(
                "`androidx.core:core` has META-INF/NOTICE.md in its jar, which android/legal/apache-2.0.txt does not "
                        + "carry: add its text there under a line `==== META-INF/NOTICE.md of core ====`",
                "`tools.jackson.core:jackson-core` has META-INF/NOTICE.txt in its jar, which android/legal/jackson.txt "
                        + "does not carry: add its text there under a line `==== META-INF/NOTICE.txt of jackson-core "
                        + "====`");
    }

    @Test
    void aNoticeCarriedForAnotherArtifactDoesNotCount() {
        var jackson = JACKSON.replace("==== META-INF/NOTICE of jackson-core ====", "==== META-INF/NOTICE of jackson ===");

        assertThat(problems(Map.of("apache-2.0.txt", APACHE, "jackson.txt", jackson))).singleElement().asString()
                .startsWith("`tools.jackson.core:jackson-core` has META-INF/NOTICE in its jar");
    }

    @Test
    void theProblemsAreSorted() {
        var used = Map.of("releaseRuntimeClasspath", List.of("org.b:b", "org.a:a", "androidx.core:core",
                "org.jetbrains.kotlin:kotlin-stdlib", "tools.jackson.core:jackson-core"));

        var found = problems(used, NOTICES, Map.of("apache-2.0.txt", APACHE, "jackson.txt", JACKSON),
                List.of(ICONS, "z/Z.kt", "a/A.kt"), Set.of(ICONS));

        assertThat(found).hasSize(4).isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*\n * Copyright 2025 The Android Open Source Project\n */",
        "// Copyright (C) 2019 Someone\nclass A", "<!-- Copyright 2020 Someone -->", "# SPDX-License-Identifier: MIT",
        "/* (c) 2001 Someone */", "/** Copyright 2024 Someone */"})
    void aCommentLineThatStartsWithACopyrightIsAThirdPartysHeader(String text) {
        assertThat(LegalFiles.hasThirdPartyHeader(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/** The screen of the licences, whose copyright line comes from NOTICE. */",
        "val line = \"Copyright (C) 2026\"", "Copyright 2026 in a plain line, not a comment",
        "// the copyright line of the app"})
    void theAppsOwnTextsAboutACopyrightAreNoHeader(String text) {
        assertThat(LegalFiles.hasThirdPartyHeader(text)).isFalse();
    }
}
