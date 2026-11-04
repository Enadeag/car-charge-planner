package nl.enadeag.carchargeplanner.buildlogic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
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

    private LegalFilesCheck task(String jar) {
        var project = ProjectBuilder.builder().withProjectDir(dir.resolve("android/app").toFile()).build();
        var check = project.getTasks().register("checkLegalFiles", LegalFilesCheck.class).get();
        check.getLibraries().put("releaseRuntimeClasspath", List.of("g:core"));
        check.getJars().put("g:core", List.of(jar));
        check.getLegal().set(dir.resolve("android/legal").toFile());
        check.getSources().set(dir.resolve("android/app/src").toFile());
        check.getRepository().set(dir.toFile());
        return check;
    }

    @Test
    void filesThatMatchWhatTheAppBringsPass() throws IOException {
        write(COPIED, "// © 2023 Someone\nclass Copied\n");
        write("android/legal/apache-2.0.txt", "Apache License 2.0\r\nArtifact: g:core\r\n\r\nCopied: an icon, in\r\n"
                + COPIED + "\r\nFrom: somewhere\r\n==== META-INF/NOTICE of core ====\r\n");

        assertThatCode(task(jar("core.jar", "META-INF/NOTICE"))::check).doesNotThrowAnyException();
    }

    @Test
    void aSourceWhoseHeaderHasAUtf8CopyrightSignAndNoCopiedBlockFailsTheCheck() throws IOException {
        write(COPIED, "// © 2023 Someone\nclass Copied\n");
        write("android/legal/apache-2.0.txt", "Apache License 2.0\nArtifact: g:core\n");

        assertThatThrownBy(task(jar("core.jar"))::check).isInstanceOf(GradleException.class)
                .hasMessageContaining("`" + COPIED + "` has a third party's header but is in no `Copied:` block");
    }

    @Test
    void theNoticesOfAJarAreItsNoticeFilesRightInMetaInfInAnyCase() throws IOException {
        var core = jar("core.jar", "META-INF/NOTICE", "META-INF/notice.txt", "META-INF/LICENSE", "NOTICE",
                "META-INF/NOTICES/", "META-INF/NOTICE.d/more", "META-INF/maven/g/core/NOTICE");
        var annotations = jar("annotations.jar", "META-INF/MANIFEST.MF");

        assertThat(LegalFilesCheck.notices(Map.of("g:core", List.of(core), "g:annotations", List.of(annotations))))
                .containsExactly(entry("g:core", List.of("META-INF/NOTICE", "META-INF/notice.txt")));
    }

    @Test
    void aLibrarysNoticesComeFromEveryJarOfIt() throws IOException {
        var plain = jar("plain.jar", "META-INF/MANIFEST.MF");
        var withNotice = jar("with-notice.jar", "META-INF/NOTICE");

        assertThat(LegalFilesCheck.notices(Map.of("g:core", List.of(plain, withNotice))))
                .containsExactly(entry("g:core", List.of("META-INF/NOTICE")));
    }

    @Test
    void aSymbolicLinkInTheSourcesIsReportedAndNotFollowed() throws IOException {
        var outside = write("outside.kt", "// © 2023 Someone\n");
        write("android/app/src/main/kotlin/Own.kt", "class Own\n");
        symbolicLink(dir.resolve("android/app/src/main/kotlin/Link.kt"), outside);
        var root = dir.toAbsolutePath().normalize();

        assertThatThrownBy(() -> LegalFilesCheck.withHeader(root, root.resolve("android/app/src")))
                .isInstanceOf(GradleException.class)
                .hasMessageContainingAll("`android/app/src/main/kotlin/Link.kt` is a symbolic link",
                        "replace it with the file itself");
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

        assertThat(LegalFilesCheck.withHeader(root, root.resolve("android/app/src"))).containsExactlyInAnyOrder(
                "android/app/src/main/kotlin/Icons.kt", "android/app/src/main/java/Old.java",
                "android/app/src/main/res/drawable/icon.xml");
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
