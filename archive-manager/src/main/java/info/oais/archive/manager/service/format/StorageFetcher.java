package info.oais.archive.manager.service.format;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Fetches a Data Object's bits from its storage location
 * ({@code im:hasStorageLocation}) so the server can decode them, e.g. to
 * serve them as VOTable. Storage locations are set by editors but fetched
 * when anyone asks, so this is careful about what it will fetch:
 * <ul>
 *   <li>only {@code http} and {@code https};</li>
 *   <li>not from loopback, link-local, private-network or other non-public
 *       addresses (checked for every redirect too), unless
 *       {@code archive.fetch.allow-private-addresses} is true -- so a storage
 *       location can't be used to reach services inside the server's network;</li>
 *   <li>at most {@code archive.fetch.max-bytes}, within
 *       {@code archive.fetch.timeout-seconds}.</li>
 * </ul>
 * The address check happens before connecting; a host whose DNS answer
 * changes between the check and the connection could still slip through, so
 * don't rely on this alone where that matters.
 */
@Component
public class StorageFetcher {

    private static final int MAX_REDIRECTS = 5;

    private final long maxBytes;
    private final boolean allowPrivateAddresses;
    private final Duration timeout;

    public StorageFetcher(@Value("${archive.fetch.max-bytes:104857600}") long maxBytes,
                          @Value("${archive.fetch.allow-private-addresses:false}") boolean allowPrivateAddresses,
                          @Value("${archive.fetch.timeout-seconds:60}") long timeoutSeconds) {
        this.maxBytes = maxBytes;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Fetches {@code location} into a new file in {@code directory}.
     *
     * @throws IOException saying why it wasn't fetched
     */
    public Path fetch(URI location, Path directory) throws IOException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI current = location;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            check(current);
            HttpResponse<InputStream> response;
            try {
                response = client.send(HttpRequest.newBuilder(current).timeout(timeout).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + current);
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.headers().firstValue("Location").isPresent()) {
                response.body().close();
                current = current.resolve(response.headers().firstValue("Location").get());
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("Fetching " + current + " failed: HTTP " + status);
            }
            Path file = Files.createTempFile(directory, "data-", ".bin");
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                for (int n; (n = in.read(buffer)) > 0; ) {
                    total += n;
                    if (total > maxBytes) {
                        throw new IOException(location + " is larger than " + maxBytes + " bytes, the most this "
                                + "server fetches (archive.fetch.max-bytes)");
                    }
                    out.write(buffer, 0, n);
                }
            }
            return file;
        }
        throw new IOException("Fetching " + location + " was redirected more than " + MAX_REDIRECTS + " times");
    }

    private void check(URI location) throws IOException {
        String scheme = location.getScheme() == null ? "" : location.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("The server only fetches data from http and https locations, not " + location);
        }
        if (allowPrivateAddresses) {
            return;
        }
        String host = location.getHost();
        if (host == null) {
            throw new IOException(location + " names no host");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IOException("Unknown host " + host);
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IOException("The server doesn't fetch data from " + host + " (" + address.getHostAddress()
                        + "): it's not a public internet address (archive.fetch.allow-private-addresses)");
            }
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) != 0xFC; // fc00::/7, unique local
        }
        int first = b[0] & 0xFF, second = b[1] & 0xFF;
        return !(first == 0 || first == 100 && second >= 64 && second < 128 // carrier-grade NAT
                || first == 192 && second == 0 && (b[2] & 0xFF) == 0 || first >= 224);
    }
}
