package nl.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Answers from recorded routes, so no test touches the network; a request no route matches fails the test. A route
 * that holds an exception throws it, which is how "no network" is played.
 */
public final class ReplayHttp implements HttpGet {

    /** Thrown for a request the test did not record. */
    public static final class UnexpectedRequest extends RuntimeException {
        UnexpectedRequest(Request request) {
            super("the provider asked for " + request.uri() + ", which the test did not record");
        }
    }

    public record Route(Predicate<Request> match, Reply reply, IOException failure) {

        /** Answers every request whose address contains {@code part}; an empty part matches everything. */
        public static Route answer(String part, Reply reply) {
            return new Route(request -> request.uri().toString().contains(part), reply, null);
        }

        public static Route failure(IOException failure) {
            return new Route(request -> true, null, failure);
        }
    }

    public final List<Request> requests = new ArrayList<>();
    /** Also kept here, in case the provider swallowed the exception. */
    public final List<Request> unexpected = new ArrayList<>();
    private final List<Route> routes;

    public ReplayHttp(List<Route> routes) {
        this.routes = List.copyOf(routes);
    }

    public static ReplayHttp always(Reply reply) {
        return new ReplayHttp(List.of(Route.answer("", reply)));
    }

    @Override
    public Reply get(Request request) throws IOException {
        requests.add(request);
        for (var route : routes) {
            if (route.match().test(request)) {
                if (route.failure() != null) {
                    throw route.failure();
                }
                return route.reply();
            }
        }
        unexpected.add(request);
        throw new UnexpectedRequest(request);
    }
}
