package nl.enadeag.carchargeplanner.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import nl.enadeag.carchargeplanner.engine.forecast.ByteSize;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;

/**
 * Fetches web addresses with {@link HttpURLConnection}: the Android app's {@link HttpGet}, and the one class in the app
 * that opens a network connection. Each request runs on a worker thread while the caller waits at most the request's
 * timeout; when that passes, or the caller is interrupted, the connection is dropped and the caller goes on. So the
 * deadline frees the caller during the name lookup too, which {@link HttpURLConnection}'s own timeouts leave out; those
 * are set to the request's timeout as well, so a worker that was left behind ends after connecting or reading. A worker
 * stuck in the name lookup keeps its daemon thread until the resolver gives up: the pool has no bound and is never shut
 * down, and its idle threads end after a minute.
 *
 * <p>It asks for the body without compression ({@code Accept-Encoding: identity}): Android's {@link HttpURLConnection}
 * would otherwise ask for gzip and unpack it, the JVM's would not, and the size limit counts the bytes as they come.
 * The request's own headers cannot replace that or the User-Agent. Redirects are returned, not followed; a status of
 * 400 and up still gives its body; an answer that has no body by definition (1xx, 204, 304) gives an empty one; the
 * body is read as UTF-8; of a header sent more than once, the first value counts.
 */
public final class AndroidHttpGet implements HttpGet {

    /** The desktop's User-Agent ({@code JdkHttpGet}), so the services see one app: no device or version in it. */
    static final String USER_AGENT = "car-charge-planner (+https://github.com/Enadeag/car-charge-planner)";

    private static final int CHUNK = 8 * 1024;

    private final AtomicInteger threads = new AtomicInteger();

    // a cached pool: a request whose name lookup hangs keeps its thread until the lookup ends, without holding up the
    // next request
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        var thread = new Thread(runnable, "http-get-" + threads.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /** Makes the fetcher; it holds no connection between requests. */
    public AndroidHttpGet() {
    }

    @Override
    public Reply get(Request request) throws IOException, InterruptedException {
        URLConnection opened = request.uri().toURL().openConnection(); // only makes the object; nothing is sent yet
        if (!(opened instanceof HttpURLConnection connection)) {
            throw new IOException("not a web address: " + request.uri().getScheme());
        }
        int backstop = (int) Math.max(1, Math.min(Integer.MAX_VALUE, request.timeout().toMillis()));
        connection.setConnectTimeout(backstop);
        connection.setReadTimeout(backstop);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        request.headers().forEach(connection::setRequestProperty);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "identity");

        Future<Reply> answer = workers.submit(() -> fetch(connection, request.maxBody()));
        try {
            return answer.get(request.timeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            drop(answer, connection);
            throw new SocketTimeoutException("no whole answer within " + request.timeout().toMillis() + " ms");
        } catch (InterruptedException e) {
            drop(answer, connection);
            throw e;
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        }
    }

    /**
     * Ends a request the caller no longer waits for: the worker stops at its next read, as it is interrupted, and the
     * connection is dropped. Dropping it runs on a thread of its own, because the JVM's {@link HttpURLConnection} can
     * hold {@code disconnect()} for as long as the worker is reading; Android's returns at once.
     */
    private void drop(Future<Reply> answer, HttpURLConnection connection) {
        answer.cancel(true);
        workers.execute(connection::disconnect);
    }

    /** Runs on a worker: connects, reads the status, the headers and the body, and always drops the connection. */
    private static Reply fetch(HttpURLConnection connection, ByteSize limit) throws IOException {
        try {
            int status = connection.getResponseCode();
            var headers = firstValues(connection);
            if (status < 200 || status == 204 || status == 304) {
                // no body by definition, whatever Content-Length says: a 304 may give the full answer's length
                return new Reply(status, "", headers);
            }
            long declared = connection.getContentLengthLong();
            if (declared > limit.bytes()) {
                throw new TooLarge(limit);
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = stream == null ? new byte[0] : read(stream, limit);
            if (declared >= 0 && body.length != declared) {
                // the JVM's HttpURLConnection hands back a body that was cut short as if it were whole
                throw new IOException("the answer ended after " + body.length + " of " + declared + " bytes");
            }
            return new Reply(status, new String(body, StandardCharsets.UTF_8), headers);
        } finally {
            connection.disconnect();
        }
    }

    /** The headers by name, in any case, with the first value of each; the status line, which has no name, left out. */
    private static Map<String, String> firstValues(HttpURLConnection connection) {
        var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        connection.getHeaderFields().forEach((name, values) -> {
            if (name != null && !values.isEmpty()) {
                headers.putIfAbsent(name, values.get(0));
            }
        });
        return headers;
    }

    private static byte[] read(InputStream stream, ByteSize limit) throws IOException {
        try (stream) {
            var bytes = new ByteArrayOutputStream();
            var chunk = new byte[CHUNK];
            for (int n; (n = stream.read(chunk)) != -1; ) {
                if (bytes.size() + (long) n > limit.bytes()) {
                    throw new TooLarge(limit);
                }
                bytes.write(chunk, 0, n);
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("the caller no longer waits for the answer");
                }
            }
            return bytes.toByteArray();
        }
    }

    /**
     * What the worker failed with, as the exception {@link HttpGet#get} promises: an {@link IOException} as it is,
     * and anything else but an {@link Error} wrapped in one.
     *
     * @param cause what the worker failed with, not null
     * @return the exception to throw
     * @throws Error when the cause is one, as it is
     */
    static IOException failure(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return cause instanceof IOException io ? io : new IOException(cause);
    }
}
