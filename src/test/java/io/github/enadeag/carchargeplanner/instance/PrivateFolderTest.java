package io.github.enadeag.carchargeplanner.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Making the user's own folder that others may read owner-only, and refusing it when that cannot be done safely. */
@EnabledOnOs(OS.LINUX)
class PrivateFolderTest {

    @TempDir
    Path temp;

    private Path folder;
    private Optional<UserPrincipal> user;

    @BeforeEach
    void anOwnFolderOthersMayRead() throws IOException {
        folder = Files.createDirectory(temp.resolve("folder"));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
        user = Optional.of(Files.getOwner(temp));
    }

    private String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    @Test
    void itIsMadeOwnerOnly() throws IOException {
        assertThat(new PrivateFolder(folder, user).make()).isEqualTo(folder);
        assertThat(mode(folder)).isEqualTo("rwx------");
    }

    @Test
    void whenItsModeCannotBeChangedItIsRefused() throws IOException {
        var folderThatResists = new PrivateFolder(folder, user, (path, mode) -> {
            throw new IOException("operation not permitted");
        });
        assertThatThrownBy(folderThatResists::make).isInstanceOf(NotPrivateException.class)
                .hasMessageContaining("cannot be made owner-only");
        assertThat(mode(folder)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void whenAnotherFolderTakesItsPlaceBeforeTheChangeItIsRefused() throws IOException {
        var original = temp.resolve("original");
        var swapped = new PrivateFolder(folder, user, (path, mode) -> {
            // the checked folder moves away and a new one, owner-only already, takes its place
            Files.move(path, original);
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(mode));
        });
        assertThatThrownBy(swapped::make).isInstanceOf(NotPrivateException.class)
                .hasMessageContaining("not the same owner-only folder");
    }
}
