package nl.enadeag.carchargeplanner.buildlogic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegalFilesCheckTest {

    private static final String COPIED = "android/app/src/main/kotlin/Copied.kt";

    @TempDir
    Path dir;

    private Path write(String path, String text) throws IOException {
        var file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text, UTF_8);
    }

    private String jar(String name, String... entries) throws IOException {
        var jar = dir.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.closeEntry();
            }
        }
        return jar.toString();
    }

    private String jarWith(String name, String entry, String text) throws IOException {
        var jar = dir.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry(entry));
            out.write(text.getBytes(UTF_8));
            out.closeEntry();
        }
        return jar.toString();
    }

    private LegalFilesCheck task(String jar) {
        var project = ProjectBuilder.builder().withProjectDir(dir.resolve("android/app").toFile()).build();
        var check = project.getTasks().register("checkLegalFiles", LegalFilesCheck.class).get();
        check.getLibraries().put("releaseRuntimeClasspath", List.of("g:core"));
        check.getJars().put("g:core", List.of(jar));
        check.getLegal().set(dir.resolve("android/legal").toFile());
        check.getSources().from(dir.resolve("android/app/src").toFile(), dir.resolve("engine/src/main").toFile());
        check.getRepository().set(dir.toFile());
        return check;
    }

    @Test
    void filesThatMatchWhatTheAppBringsPass() throws IOException {
        Files.createDirectories(dir.resolve("engine/src/main"));
        write(COPIED, "// © 2023 Someone\nclass Copied\n");
        write("android/legal/apache-2.0.txt", "Apache License 2.0\r\nArtifact: g:core\r\n\r\nCopied: an icon, in\r\n"
                + COPIED + "\r\nFrom: somewhere\r\n==== META-INF/NOTICE of core ====\r\n");

        assertThatCode(task(jar("core.jar", "META-INF/NOTICE"))::check).doesNotThrowAnyException();
    }

    @Test
    void aSourceWhoseHeaderHasAUtf8CopyrightSignAndNoCopiedBlockFailsTheCheck() throws IOException {
        write(COPIED, "// © 2023 Someone\nclass Copied\n");
        write("android/legal/apache-2.0.txt", "Apache License 2.0\nArtifact: g:core\n");
        Files.createDirectories(dir.resolve("engine/src/main"));

        assertThatThrownBy(task(jar("core.jar"))::check).isInstanceOf(GradleException.class)
                .hasMessageContaining("`" + COPIED + "` has a third party's header but is in no `Copied:` block");
    }

    @Test
    void aSourceInTheEnginesMainSourcesWithAThirdPartysHeaderAndNoCopiedBlockFailsTheCheck() throws IOException {
        write("engine/src/main/java/Taken.java", "// SPDX-License-Identifier: MIT\nclass Taken {}\n");
        write("android/app/src/main/kotlin/Own.kt", "class Own\n");
        write("android/legal/apache-2.0.txt", "Apache License 2.0\nArtifact: g:core\n");

        assertThatThrownBy(task(jar("core.jar"))::check).isInstanceOf(GradleException.class)
                .hasMessageContaining("`engine/src/main/java/Taken.java` has a third party's header but is in no "
                        + "`Copied:` block");
    }

    @Test
    void aNoticeTextThatDiffersFromTheJarsFailsTheCheckAndOneThatDiffersInWhitespaceOnlyPasses() throws IOException {
        Files.createDirectories(dir.resolve("engine/src/main"));
        Files.createDirectories(dir.resolve("android/app/src"));
        write("android/legal/apache-2.0.txt", "Apache License 2.0\nArtifact: g:core\n"
                + "==== META-INF/NOTICE of core ====\nOld text  \r\n\r\n");

        assertThatCode(task(jarWith("same.jar", "META-INF/NOTICE", "Old text\n"))::check).doesNotThrowAnyException();
        assertThatThrownBy(task(jarWith("new.jar", "META-INF/NOTICE", "New text\n"))::check)
                .isInstanceOf(GradleException.class)
                .hasMessageContainingAll("android/legal/apache-2.0.txt", "META-INF/NOTICE of core",
                        "take over the jar's new NOTICE text");
    }

    @Test
    void theNoticesOfAJarAreItsNoticeFilesRightInMetaInfInAnyCaseWithTheirTexts() throws IOException {
        var core = dir.resolve("core.jar");
        try (var out = new ZipOutputStream(Files.newOutputStream(core))) {
            for (var name : List.of("META-INF/NOTICE", "META-INF/notice.txt", "META-INF/LICENSE", "NOTICE",
                    "META-INF/NOTICES/", "META-INF/NOTICE.d/more", "META-INF/maven/g/core/NOTICE")) {
                out.putNextEntry(new ZipEntry(name));
                out.write(("text of " + name + "\r\nsecond \u00e9\r\n").getBytes(UTF_8));
                out.closeEntry();
            }
        }
        var annotations = jar("annotations.jar", "META-INF/MANIFEST.MF");

        assertThat(LegalFilesCheck.notices(Map.of("g:core", List.of(core.toString()),
                "g:annotations", List.of(annotations)))).containsExactly(entry("g:core", Map.of(
                "META-INF/NOTICE", "text of META-INF/NOTICE\nsecond \u00e9\n",
                "META-INF/notice.txt", "text of META-INF/notice.txt\nsecond \u00e9\n")));
    }

    @Test
    void aLibrarysNoticesComeFromEveryJarOfItAndTheFirstJarsTextWins() throws IOException {
        var plain = jar("plain.jar", "META-INF/MANIFEST.MF");
        var withNotice = jarWith("with-notice.jar", "META-INF/NOTICE", "first");
        var other = jarWith("other.jar", "META-INF/NOTICE", "second");

        assertThat(LegalFilesCheck.notices(Map.of("g:core", List.of(plain, withNotice, other))))
                .containsExactly(entry("g:core", Map.of("META-INF/NOTICE", "first")));
    }

    @Test
    void aSymbolicLinkInTheSourcesIsReportedAndNotFollowed() throws IOException {
        var outside = write("outside.kt", "// © 2023 Someone\n");
        write("android/app/src/main/kotlin/Own.kt", "class Own\n");
        symbolicLink(dir.resolve("android/app/src/main/kotlin/Link.kt"), outside);
        var root = dir.toAbsolutePath().normalize();

        assertThatThrownBy(() -> LegalFilesCheck.withHeader(root, List.of(root.resolve("android/app/src"))))
                .isInstanceOf(GradleException.class)
                .hasMessageContainingAll("`android/app/src/main/kotlin/Link.kt` is a symbolic link",
                        "replace it with the file itself");
    }

    @Test
    void aMissingSourceFolderFailsInsteadOfBeingSkipped() {
        var root = dir.toAbsolutePath().normalize();

        assertThatThrownBy(() -> LegalFilesCheck.withHeader(root, List.of(root.resolve("engine/src/main"))))
                .isInstanceOf(UncheckedIOException.class).hasMessageContaining("cannot read");
    }

    @Test
    void aCopiedPathThatIsASymbolicLinkIsNoFile() throws IOException {
        var target = write("repo/android/app/Icons.kt", "class Icons\n");
        symbolicLink(dir.resolve("repo/android/app/Alias.kt"), target);
        var exists = LegalFilesCheck.inRepository(dir.resolve("repo").toAbsolutePath().normalize());

        assertThat(exists.test("android/app/Icons.kt")).isTrue();
        assertThat(exists.test("android/app/Alias.kt")).isFalse();
    }

    @Test
    void aCopiedPathThroughALinkedFolderIsNoFile() throws IOException {
        write("repo/android/real/Icons.kt", "class Icons\n");
        symbolicLink(dir.resolve("repo/android/alias"), dir.resolve("repo/android/real"));
        var exists = LegalFilesCheck.inRepository(dir.resolve("repo").toAbsolutePath().normalize());

        assertThat(exists.test("android/real/Icons.kt")).isTrue();
        assertThat(exists.test("android/alias/Icons.kt")).isFalse();
    }

    private static void symbolicLink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("this system makes no symbolic links: " + e);
        }
    }

    @Test
    void everySourceFileWithAThirdPartysHeaderIsFoundWhateverItsKind() throws IOException {
        write("android/app/src/main/kotlin/Icons.kt", "/*\n * Copyright 2024 The Android Open Source Project\n */\n");
        write("android/app/src/main/java/Old.java", "// SPDX-License-Identifier: Apache-2.0\nclass Old {}\n");
        write("android/app/src/main/res/drawable/icon.xml", "<!-- Copyright 2020 Someone -->\n<vector/>\n");
        write("android/app/src/main/kotlin/Own.kt", "/** The app's own. */\nclass Own\n");
        Files.write(dir.resolve("android/app/src/main/res/drawable/image.png"),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) 0xFF, (byte) 0xFE, 0, (byte) 0xC3});
        var root = dir.toAbsolutePath().normalize();

        write("engine/src/main/java/Engine.java", "// Copyright 2024 Someone\nclass Engine {}\n");
        write("engine/src/main/java/Plain.java", "class Plain {}\n");

        assertThat(LegalFilesCheck.withHeader(root,
                List.of(root.resolve("android/app/src"), root.resolve("engine/src/main"))))
                .containsExactlyInAnyOrder("android/app/src/main/kotlin/Icons.kt",
                        "android/app/src/main/java/Old.java", "android/app/src/main/res/drawable/icon.xml",
                        "engine/src/main/java/Engine.java");
    }

    @Test
    void aCopiedPathCountsOnlyForAFileInTheRepository() throws IOException {
        write("repo/android/app/Icons.kt", "class Icons\n");
        write("outside.kt", "class Outside\n");
        var exists = LegalFilesCheck.inRepository(dir.resolve("repo").toAbsolutePath().normalize());

        assertThat(exists.test("android/app/Icons.kt")).isTrue();
        assertThat(exists.test("android/app")).isFalse();
        assertThat(exists.test("android/app/Gone.kt")).isFalse();
        assertThat(exists.test("../outside.kt")).isFalse();
    }
}
