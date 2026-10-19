package nl.enadeag.carchargeplanner.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SingleInstanceTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);

    @TempDir
    Path temp;
    private Path folder;
    private Optional<UserPrincipal> user;
    private final BlockingQueue<InstanceCommand> received = new LinkedBlockingQueue<>();
    private final List<AutoCloseable> toClose = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        folder = temp.resolve("instance");
        user = hasFileModes() ? Optional.of(Files.getOwner(temp)) : Optional.empty();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (var closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    private SingleInstance instance() {
        var instance = new SingleInstance(folder, user, TIMEOUT);
        toClose.add(instance);
        return instance;
    }

    private SingleInstance first() {
        var first = instance();
        assertThat(first.claim(received::add)).isEqualTo(Claim.FIRST);
        return first;
    }

    private Path socket() {
        return folder.resolve("instance.sock");
    }

    private boolean hasFileModes() {
        return temp.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private InstanceCommand nextReceived() throws InterruptedException {
        return received.poll(5, TimeUnit.SECONDS);
    }

    @Test
    void aSecondStartHandsOverAndTheFirstIsAskedToShowItsDashboard() throws Exception {
        first();
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.HANDED_OVER);
        assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
    }

    @Test
    void quitIsConfirmedAndDeliveredWithoutShowing() throws Exception {
        first();
        assertThat(instance().send(InstanceCommand.QUIT)).isEqualTo(Reply.ANSWERED);
        assertThat(nextReceived()).isEqualTo(InstanceCommand.QUIT);
        assertThat(received).isEmpty();
    }

    @Test
    void withoutARunningCopyNobodyAnswers() throws Exception {
        assertThat(instance().send(InstanceCommand.QUIT)).isEqualTo(Reply.NOBODY);
    }

    @Test
    void afterTheRunningCopyClosesTheSocketIsGoneAndTheNextStartIsFirst() throws Exception {
        first().close();
        assertThat(socket()).doesNotExist();
        first();
    }

    @Test
    void aSocketLeftBehindByACrashIsTakenOver() throws Exception {
        Files.createDirectory(folder, privateFolder());
        try (var crashed = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            crashed.bind(UnixDomainSocketAddress.of(socket())); // closing it leaves the file, as a crash does
        }
        assertThat(socket()).exists();
        assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.STALE);

        first();
        assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aSocketThatCannotBeReachedIsReplacedByTheCopyThatTakesTheLock() throws Exception {
        Files.createDirectory(folder, privateFolder());
        try (var other = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            other.bind(UnixDomainSocketAddress.of(socket()));
            Files.setPosixFilePermissions(socket(), PosixFilePermissions.fromString("---------"));
            assumeTrue(!Files.isWritable(socket()), "root may connect to any socket");

            first();
            assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);
        }
    }

    @Test
    void anOlderVersionThatListensWithoutTakingTheLockIsHandedOverTo() throws Exception {
        Files.createDirectory(folder, privateFolder());
        try (var older = InstanceServer.bind(socket(), user, TIMEOUT)) {
            older.serve(received::add);
            assertThat(instance().claim(command -> { })).isEqualTo(Claim.HANDED_OVER);
            assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
        }
        first(); // the lock was let go of again
    }

    @Test
    void aCopyThatHoldsTheSocketButDoesNotAnswerIsNeitherReplacedNorJoined() throws Exception {
        Files.createDirectory(folder, privateFolder());
        try (var mute = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            mute.bind(UnixDomainSocketAddress.of(socket()));
            assertThat(instance().claim(command -> { })).isEqualTo(Claim.NO_ANSWER);
            assertThat(socket()).exists();
        }
    }

    @Test
    void twoCopiesStartingAtOnceEndWithOneRunningAndTheOtherHandingOver() throws Exception {
        var start = new CountDownLatch(1);
        Callable<Claim> claim = () -> {
            var instance = instance();
            start.await();
            return instance.claim(received::add);
        };
        try (var threads = Executors.newFixedThreadPool(2)) {
            var one = threads.submit(claim);
            var other = threads.submit(claim);
            start.countDown();
            assertThat(List.of(one.get(), other.get())).containsExactlyInAnyOrder(Claim.FIRST, Claim.HANDED_OVER);
        }
        assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
    }

    @Test
    void aPeerThatSendsNothingIsCutOffAndHoldsUpNoOne() throws Exception {
        first();
        try (var silent = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
            assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);
            assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
            // the server closes the connection after its timeout: the read ends instead of waiting forever
            assertThat(silent.read(ByteBuffer.allocate(8))).isEqualTo(-1);
        }
    }

    @Test
    void linesThatAreNoCommandAreNeitherAnsweredNorCarriedOutNorLogged() throws Exception {
        first();
        var logged = new ArrayList<String>();
        var logger = Logger.getLogger(InstanceServer.class.getName());
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var level = logger.getLevel();
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try (var channel = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
            Lines.write(channel, "rm -rf ~");
            assertThat(channel.read(ByteBuffer.allocate(8))).isEqualTo(-1);
            assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);
            assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(true);
            logger.setLevel(level);
        }
        assertThat(received).isEmpty();
        assertThat(String.join("\n", logged)).contains("\"show\"").doesNotContain("rm -rf");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theFolderAndTheSocketAreTheUsersOnly() throws Exception {
        first();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(socket()))).isEqualTo("rw-------");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theUsersOwnFolderOpenToOthersIsMadeOwnerOnlyAndUsed() throws Exception {
        Files.createDirectory(folder, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo("rwxr-xr-x");

        var warnings = new ArrayList<String>();
        var logger = Logger.getLogger(PrivateFolder.class.getName());
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == java.util.logging.Level.WARNING) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            first();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(true);
        }
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo("rwx------");
        assertThat(warnings).singleElement().asString().contains("tightened").contains("owner-only");
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.HANDED_OVER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rwxrwxr-x", "rwxrwxrwx", "rwx-w----"})
    @EnabledOnOs(OS.LINUX)
    void theUsersOwnFolderThatOthersMayWriteInIsNotUsedAndNoCopyRuns(String mode) throws Exception {
        Files.createDirectory(folder);
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString(mode));
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo(mode);
        assertThat(folder).isEmptyDirectory();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aFolderOfAnotherUserIsNotUsedAndNoCopyRuns() throws Exception {
        user = Optional.of(temp.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root"));
        assumeTrue(!user.equals(Optional.of(Files.getOwner(temp))), "the tests run as root");
        var first = instance();
        assertThat(first.claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(first.problem()).hasValueSatisfying(problem -> assertThat(problem).contains("another user"));
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(folder).isEmptyDirectory();
    }

    @Test
    void aLinkInPlaceOfTheFolderIsNotFollowed() throws Exception {
        var elsewhere = Files.createDirectory(temp.resolve("elsewhere"), privateFolder());
        try {
            Files.createSymbolicLink(folder, elsewhere);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "this account may not make links: " + e);
        }
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.NOT_PRIVATE);
        assertThat(elsewhere).isEmptyDirectory();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aConnectionOfAnotherUserIsRefused() throws Exception {
        Files.createDirectory(folder, privateFolder());
        var root = temp.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
        assumeTrue(!root.equals(Files.getOwner(temp)), "the tests run as root");
        try (var server = InstanceServer.bind(socket(), Optional.of(root), TIMEOUT)) {
            server.serve(received::add);
            assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.SILENT);
        }
        assertThat(received).isEmpty();
    }

    @Test
    void withASocketPathTooLongForASocketTheLockStillKeepsASecondCopyOut() throws Exception {
        useTooLongPath();
        var first = instance();
        assertThat(first.claim(received::add)).isEqualTo(Claim.FIRST_UNREACHABLE);
        assertThat(first.problem()).hasValueSatisfying(problem -> assertThat(problem).contains("instance.sock"));

        assertThat(instance().claim(command -> { })).isEqualTo(Claim.UNREACHABLE);
        assertThat(instance().send(InstanceCommand.QUIT)).isEqualTo(Reply.UNREACHABLE);
        assertThat(received).isEmpty();

        first.close();
        assertThat(instance().send(InstanceCommand.QUIT)).isEqualTo(Reply.NOBODY);
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.FIRST_UNREACHABLE);
    }

    @Test
    void twoCopiesStartingAtOnceWithASocketPathTooLongEndWithOneRunning() throws Exception {
        useTooLongPath();
        var start = new CountDownLatch(1);
        Callable<Claim> claim = () -> {
            var instance = instance();
            start.await();
            return instance.claim(received::add);
        };
        try (var threads = Executors.newFixedThreadPool(2)) {
            var one = threads.submit(claim);
            var other = threads.submit(claim);
            start.countDown();
            assertThat(List.of(one.get(), other.get()))
                    .containsExactlyInAnyOrder(Claim.FIRST_UNREACHABLE, Claim.UNREACHABLE);
        }
    }

    @Test
    void theLockOfACopyInAnotherProcessKeepsThisOneOutUntilThatProcessDies() throws Exception {
        useTooLongPath();
        var other = startClaimingProcess();
        assertThat(firstLine(other)).isEqualTo(Claim.FIRST_UNREACHABLE.name());
        assertThat(instance().claim(command -> { })).isEqualTo(Claim.UNREACHABLE);

        other.destroyForcibly(); // as in a crash: nothing is closed
        assertThat(other.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(patient().claim(command -> { })).isEqualTo(Claim.FIRST_UNREACHABLE);
    }

    @Test
    void theSocketOfACopyInAnotherProcessThatCrashedIsTakenOver() throws Exception {
        var other = startClaimingProcess();
        assertThat(firstLine(other)).isEqualTo(Claim.FIRST.name());
        assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);

        other.destroyForcibly();
        assertThat(other.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(socket()).exists();
        var next = patient();
        assertThat(next.claim(received::add)).isEqualTo(Claim.FIRST);
        assertThat(instance().send(InstanceCommand.SHOW)).isEqualTo(Reply.ANSWERED);
        assertThat(nextReceived()).isEqualTo(InstanceCommand.SHOW);
    }

    /** A folder whose socket's path is longer than the 108 bytes a Unix domain socket's path may have. */
    private void useTooLongPath() {
        folder = temp.resolve("a-folder-with-a-long-name-" + "x".repeat(100));
        assertThat(socket().toString().getBytes(StandardCharsets.UTF_8).length).isGreaterThan(108);
    }

    /** A guard that waits long enough for the system to let go of a dead process's lock (Windows takes a moment). */
    private SingleInstance patient() {
        var instance = new SingleInstance(folder, user, Duration.ofSeconds(10));
        toClose.add(instance);
        return instance;
    }

    private Process startClaimingProcess() throws IOException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var process = new ProcessBuilder(java, "-Xmx64m", "-cp", System.getProperty("java.class.path"),
                ClaimingProcess.class.getName(), folder.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        toClose.add(process::destroyForcibly);
        return process;
    }

    private static String firstLine(Process process) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).get(30, TimeUnit.SECONDS);
    }

    private java.nio.file.attribute.FileAttribute<?>[] privateFolder() {
        return hasFileModes()
                ? new java.nio.file.attribute.FileAttribute<?>[] {
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new java.nio.file.attribute.FileAttribute<?>[0];
    }
}
