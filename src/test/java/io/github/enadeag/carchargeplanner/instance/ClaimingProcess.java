package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.Optional;

/**
 * A copy of the app in a process of its own, for the tests that need the lock held by another process: it claims,
 * prints the claim on a line, and holds on until it is killed or its standard input ends.
 */
final class ClaimingProcess {

    private ClaimingProcess() {
    }

    public static void main(String[] args) throws IOException {
        var folder = Path.of(args[0]);
        var parent = folder.toAbsolutePath().getParent();
        var user = parent.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? Optional.of(Files.getOwner(parent)) : Optional.<UserPrincipal>empty();
        var instance = new SingleInstance(folder, user, Duration.ofMillis(500));
        System.out.println(instance.claim(command -> { }));
        System.out.flush();
        while (System.in.read() >= 0) {
            // holds the lock until killed
        }
    }
}
