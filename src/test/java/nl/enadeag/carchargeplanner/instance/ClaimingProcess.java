package nl.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

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
        var owner = parent.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new Owner.User(Files.getOwner(parent)) : new Owner.Unchecked();
        var instance = new SingleInstance(folder, owner, Duration.ofMillis(500));
        System.out.println(instance.claim(_ -> { }));
        System.out.flush();
        while (System.in.read() >= 0) {
            // holds the lock until killed
        }
    }
}
