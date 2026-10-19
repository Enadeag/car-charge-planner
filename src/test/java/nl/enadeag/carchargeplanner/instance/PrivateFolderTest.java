package nl.enadeag.carchargeplanner.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
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
    private UserPrincipal user;

    @BeforeEach
    void anOwnFolderOthersMayRead() throws IOException {
        folder = Files.createDirectory(temp.resolve("folder"));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
        user = Files.getOwner(temp);
    }

    private String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    /** A user other than root and the one who runs the tests, as the parent may be root's. */
    private UserPrincipal otherUser() throws IOException {
        return temp.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("nobody");
    }

    private static UserPrincipal ownerOf(Path entry) throws IOException {
        return Files.getOwner(entry, LinkOption.NOFOLLOW_LINKS);
    }

    private void assertRefused(PrivateFolder privateFolder, String reason) throws IOException {
        assertThatThrownBy(privateFolder::ensurePrivate).isInstanceOf(NotPrivateException.class)
                .hasMessageContaining(reason);
        assertThat(mode(folder)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void itIsMadeOwnerOnlyWithTheUsersOwnFilesInIt() throws IOException {
        Files.writeString(folder.resolve("instance.lock"), "");
        Files.createDirectory(folder.resolve("icons"));

        assertThat(new PrivateFolder(folder, new Owner.User(user)).ensurePrivate()).isEqualTo(folder);
        assertThat(mode(folder)).isEqualTo("rwx------");
    }

    @Test
    void whenItHoldsAFileOfAnotherUserItIsRefused() throws IOException {
        var planted = Files.writeString(folder.resolve("instance.sock"), "");
        var root = temp.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
        assumeTrue(!user.equals(root), "the tests run as root");

        assertRefused(new PrivateFolder(folder, new Owner.User(user), Files::setPosixFilePermissions,
                entry -> entry.equals(planted) ? root : ownerOf(entry)), "belongs to another user");
    }

    @Test
    void whenItHoldsAFileWithAnotherNameItIsRefused() throws IOException {
        var elsewhere = Files.writeString(temp.resolve("elsewhere"), "");
        Files.createLink(folder.resolve("instance.lock"), elsewhere);

        assertRefused(new PrivateFolder(folder, new Owner.User(user)), "another name");
    }

    @Test
    void whenItsParentIsOpenToOtherUsersWhoMayWriteInItItIsRefused() throws IOException {
        var parent = Files.createDirectory(temp.resolve("shared"));
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxrwxrwx"));
        folder = Files.createDirectory(parent.resolve("folder"));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));

        assertRefused(new PrivateFolder(folder, new Owner.User(user)), "which other users may write in");
    }

    @Test
    void whenItsParentBelongsToAnotherUserItIsRefused() throws IOException {
        var other = otherUser();
        assumeTrue(!user.equals(other), "the tests run as that user");

        assertRefused(new PrivateFolder(folder, new Owner.User(user), Files::setPosixFilePermissions,
                entry -> entry.equals(temp) ? other : ownerOf(entry)), "belongs to another user");
    }

    @Test
    void whenItsParentIsALinkItIsRefused() throws IOException {
        var link = Files.createSymbolicLink(temp.resolve("link"), temp);
        folder = link.resolve("folder");

        assertRefused(new PrivateFolder(folder, new Owner.User(user)), "is a link");
    }

    @Test
    void whenItsModeCannotBeChangedItIsRefused() throws IOException {
        var folderThatResists = new PrivateFolder(folder, new Owner.User(user), (_, _) -> {
            throw new IOException("operation not permitted");
        }, PrivateFolderTest::ownerOf);
        assertThatThrownBy(folderThatResists::ensurePrivate).isInstanceOf(NotPrivateException.class)
                .hasMessageContaining("cannot be made owner-only");
        assertThat(mode(folder)).isEqualTo("rwxr-xr-x");
    }

    @Test
    void whenAnotherFolderTakesItsPlaceBeforeTheChangeItIsRefused() throws IOException {
        var original = temp.resolve("original");
        var swapped = new PrivateFolder(folder, new Owner.User(user), (path, mode) -> {
            // the checked folder moves away and a new one, owner-only already, takes its place
            Files.move(path, original);
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(mode));
        }, PrivateFolderTest::ownerOf);
        assertThatThrownBy(swapped::ensurePrivate).isInstanceOf(NotPrivateException.class)
                .hasMessageContaining("not the same owner-only folder");
    }
}
