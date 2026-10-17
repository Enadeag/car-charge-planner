package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches web addresses with Java's own HTTP client, naming the app and its home page in the User-Agent. The body is
 * read into a buffer no larger than the request allows, and the whole answer, body included, must come within the
 * request's timeout; the body is read as UTF-8, which is what JSON is sent in.
 */
public final class JdkHttpGet implements HttpGet {

    /** How long to wait for a connection to a server. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final String USER_AGENT = "car-charge-planner (+https://github.com/Enadeag/car-charge-planner)";

    private final HttpClient client;

    /**
     * Makes the fetcher.
     *
     * @param client the client to send with, normally one from {@link #newClient()}; it is shared by every request
     *     and not closed here
     */
    public JdkHttpGet(HttpClient client) {
        this.client = client;
    }

    /**
     * A client for the forecast providers: it gives up on a connection after {@link #CONNECT_TIMEOUT} and follows no
     * redirects.
     *
     * @return a new client, to be closed by the caller when the app ends
     */
    public static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Reply get(Request request) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(request.uri()).header("User-Agent", USER_AGENT).timeout(request.timeout());
        request.headers().forEach(builder::header);
        var answer = client.sendAsync(builder.GET().build(),
                info -> new BoundedBody(info.headers().firstValueAsLong("Content-Length").orElse(-1),
                        request.maxBody()));
        HttpResponse<byte[]> response;
        try {
            // the client's own timeout ends when the headers are in (Java 25), so a server that trickles its body
            // could hold the request for ever; this deadline covers the whole answer, and cancelling aborts it
            response = answer.get(request.timeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw new HttpTimeoutException("no whole answer within " + request.timeout().toMillis() + " ms");
        } catch (InterruptedException e) {
            answer.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        }
        var headers = new HashMap<String, String>();
        response.headers().map().forEach((name, values) -> headers.put(name, values.getFirst()));
        return new Reply(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8), headers);
    }

    /** What the client failed with, as the exception {@link HttpGet#get} promises. */
    private static IOException failure(Throwable cause) {
        for (var reason = cause; reason != null; reason = reason.getCause()) {
            if (reason instanceof TooLarge tooLarge) {
                return tooLarge; // the client may have wrapped it
            }
        }
        var unwrapped = cause instanceof CompletionException && cause.getCause() != null ? cause.getCause() : cause;
        if (unwrapped instanceof Error error) {
            throw error;
        }
        return unwrapped instanceof IOException io ? io : new IOException(unwrapped);
    }

    /**
     * Collects a body up to a limit, one part at a time; above it, the rest is not read and the answer fails with
     * {@link TooLarge}. A body whose declared length is above the limit fails before any of it is read.
     */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final long declaredLength;
        private final ByteSize limit;
        private Flow.Subscription subscription;

        BoundedBody(long declaredLength, ByteSize limit) {
            this.declaredLength = declaredLength;
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (declaredLength > limit.bytes()) {
                refuse();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> parts) {
            if (body.isDone()) {
                return;
            }
            for (var part : parts) {
                if (bytes.size() + (long) part.remaining() > limit.bytes()) {
                    refuse();
                    return;
                }
                var chunk = new byte[part.remaining()];
                part.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }

        private void refuse() {
            subscription.cancel();
            body.completeExceptionally(new TooLarge(limit));
        }
    }
}
