package nl.enadeag.carchargeplanner.engine.forecast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The address of a server an API key may be sent to: https anywhere, or plain http only to this computer, where the
 * key does not travel readable. It names a server and perhaps a path on it, nothing else.
 *
 * @param uri the address: scheme https with a host, or scheme http with the host localhost, 127.0.0.1 or [::1]; an
 *     optional port and path; no user info, query or fragment; no "/" at the end
 */
public record ServerAddress(URI uri) {

    /** The hosts that are this computer, as {@link URI#getHost()} gives them. */
    private static final Set<String> THIS_COMPUTER = Set.of("localhost", "127.0.0.1", "[::1]");

    /**
     * Checks the address.
     *
     * @throws IllegalArgumentException when the address is not one a key may be sent to, as described at
     *     {@code uri}
     */
    public ServerAddress {
        if (!isAllowed(uri)) {
            throw new IllegalArgumentException("the server address must be https, or http on this computer, with no "
                    + "user info, query, fragment or \"/\" at the end");
        }
    }

    /**
     * Reads an address as the owner typed it.
     *
     * @param text the address; white space around it and "/" at its end are ignored
     * @return the address, or empty when it is not a web address or not one a key may be sent to
     */
    public static Optional<ServerAddress> parse(String text) {
        var trimmed = text.strip().replaceAll("/+$", "");
        try {
            var uri = new URI(trimmed);
            return isAllowed(uri) ? Optional.of(new ServerAddress(uri)) : Optional.empty();
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
    }

    private static boolean isAllowed(URI uri) {
        if (uri.isOpaque() || uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && uri.getRawPath().endsWith("/"))) {
            return false;
        }
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        return scheme.equals("https") || (scheme.equals("http") && THIS_COMPUTER.contains(host));
    }

    /**
     * The address of something on this server.
     *
     * @param path the path to add, starting with "/"
     * @return the address as text, without a query
     */
    public String withPath(String path) {
        return uri + path;
    }
}
