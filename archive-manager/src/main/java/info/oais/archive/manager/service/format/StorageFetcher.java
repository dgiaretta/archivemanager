package info.oais.archive.manager.service.format;

import info.oais.archive.manager.service.BitStore;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.nio.file.StandardCopyOption;
import java.util.Optional;
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
 * Bits in the archive's own {@link BitStore} are read from there, not
 * fetched, whatever host their address names.
 * A file-sharing service's link to its page about a file -- a Dropbox link
 * ending {@code dl=0}, a Google Drive {@code /file/d/.../view} link, a GitHub
 * {@code /blob/} page -- is turned into the link that downloads the file
 * itself (see {@link #direct}); if such a service still answers with a web
 * page, the fetch fails saying so, rather than giving the page as the data.
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
    private final BitStore bitStore;

    @Autowired
    public StorageFetcher(@Value("${archive.fetch.max-bytes:104857600}") long maxBytes,
                          @Value("${archive.fetch.allow-private-addresses:false}") boolean allowPrivateAddresses,
                          @Value("${archive.fetch.timeout-seconds:60}") long timeoutSeconds,
                          BitStore bitStore) {
        this.bitStore = bitStore;
        this.maxBytes = maxBytes;
        this.allowPrivateAddresses = allowPrivateAddresses;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** A fetcher with no {@link BitStore}: everything is fetched over the network. */
    public StorageFetcher(long maxBytes, boolean allowPrivateAddresses, long timeoutSeconds) {
        this(maxBytes, allowPrivateAddresses, timeoutSeconds, null);
    }

    /**
     * Fetches {@code location} into a new file in {@code directory}.
     *
     * @throws IOException saying why it wasn't fetched
     */
    public Path fetch(URI location, Path directory) throws IOException {
        Optional<Path> stored = bitStore == null ? Optional.empty() : bitStore.file(location);
        if (stored.isPresent()) {
            return Files.copy(stored.get(), Files.createTempFile(directory, "data-", ".bin"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        HttpResponse<InputStream> response = open(location, null);
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

    /**
     * Up to {@code length} bytes of {@code location} from {@code offset} (fewer
     * at its end): asked for with an HTTP Range request, or read through to
     * {@code offset} when the server sends the whole file instead.
     *
     * @throws IOException saying why they weren't fetched
     */
    public byte[] fetchRange(URI location, long offset, int length) throws IOException {
        Optional<Path> stored = bitStore == null ? Optional.empty() : bitStore.file(location);
        if (stored.isPresent()) {
            try (java.nio.channels.SeekableByteChannel in = Files.newByteChannel(stored.get())) {
                in.position(offset);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(length);
                while (buffer.hasRemaining() && in.read(buffer) > 0) {
                    // read until full or at the end
                }
                return java.util.Arrays.copyOf(buffer.array(), buffer.position());
            }
        }
        HttpResponse<InputStream> response = open(location, "bytes=" + offset + "-" + (offset + length - 1));
        try (InputStream in = response.body()) {
            if (response.statusCode() == 416) {
                return new byte[0]; // the range starts past the end
            }
            if (response.statusCode() == 200) {
                long skipped = 0;
                while (skipped < offset) {
                    long n = in.skip(offset - skipped);
                    if (n <= 0) {
                        if (in.read() < 0) {
                            return new byte[0];
                        }
                        n = 1;
                    }
                    skipped += n;
                    if (skipped > maxBytes) {
                        throw new IOException(location + " is larger than " + maxBytes + " bytes, the most this "
                                + "server fetches (archive.fetch.max-bytes)");
                    }
                }
            }
            return in.readNBytes(length);
        }
    }

    /**
     * An open response for {@code location} (with {@code range}, if not null),
     * after the safety checks, following redirects: 200, or 206 for a range.
     */
    private HttpResponse<InputStream> open(URI location, String range) throws IOException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI current = direct(location);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            check(current);
            HttpRequest.Builder request = HttpRequest.newBuilder(current).timeout(timeout).GET();
            if (range != null) {
                request.header("Range", range);
            }
            HttpResponse<InputStream> response;
            try {
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
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
            if (status != 200 && !((status == 206 || status == 416) && range != null)) {
                response.body().close();
                throw new IOException("Fetching " + current + " failed: HTTP " + status);
            }
            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT);
            if (type.startsWith("text/html") && sharingService(location)) {
                response.body().close();
                throw new IOException(location + " is a file-sharing service's web page about the file, not the "
                        + "file itself (it answered with text/html). Give the storage location as the link that "
                        + "downloads the file: for Dropbox, ending dl=1; for Google Drive, "
                        + "https://drive.google.com/uc?export=download&id=...; for GitHub, the raw file's link.");
            }
            return response;
        }
        throw new IOException("Fetching " + location + " was redirected more than " + MAX_REDIRECTS + " times");
    }

    /**
     * The link that downloads a file, for a file-sharing service's link to its
     * page about the file: Dropbox's {@code dl=0} becomes {@code dl=1}, Google
     * Drive's {@code /file/d/ID/view} becomes {@code /uc?export=download&id=ID},
     * and GitHub's {@code /owner/repo/blob/ref/path} becomes the raw file at
     * {@code raw.githubusercontent.com}. Any other link is returned unchanged.
     */
    static URI direct(URI location) {
        String host = location.getHost() == null ? "" : location.getHost().toLowerCase(java.util.Locale.ROOT);
        String path = location.getRawPath() == null ? "" : location.getRawPath();
        String query = location.getRawQuery();
        try {
            if (host.equals("dropbox.com") || host.endsWith(".dropbox.com")) {
                if (path.startsWith("/s/") || path.startsWith("/scl/") || path.startsWith("/sh/")) {
                    String rest = query == null ? "" : query.replaceAll("(^|&)(dl|raw)=[^&]*", "")
                            .replaceAll("^&", "");
                    return URI.create(location.getScheme() + "://" + location.getRawAuthority() + path + "?"
                            + (rest.isEmpty() ? "" : rest + "&") + "dl=1");
                }
            }
            if (host.equals("drive.google.com")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^/file/d/([^/]+)").matcher(path);
                if (m.find()) {
                    return URI.create("https://drive.google.com/uc?export=download&id=" + m.group(1));
                }
            }
            if (host.equals("github.com")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^/([^/]+)/([^/]+)/blob/(.+)$")
                        .matcher(path);
                if (m.find()) {
                    return URI.create("https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2) + "/"
                            + m.group(3));
                }
            }
        } catch (IllegalArgumentException e) {
            return location;
        }
        return location;
    }

    /** Whether a link is to a file-sharing service, whose answer as a web page isn't the file. */
    private static boolean sharingService(URI location) {
        String host = location.getHost() == null ? "" : location.getHost().toLowerCase(java.util.Locale.ROOT);
        return host.equals("dropbox.com") || host.endsWith(".dropbox.com") || host.endsWith("drive.google.com")
                || host.equals("github.com") || host.endsWith("onedrive.live.com") || host.equals("1drv.ms")
                || host.endsWith(".sharepoint.com") || host.equals("box.com") || host.endsWith(".box.com");
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
