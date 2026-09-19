package info.oais.archive.manager.rdf;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Encodes/decodes an IRI into a URL-safe path segment, so any resource can be a route parameter. */
public final class IdCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private IdCodec() {
    }

    public static String encode(String iri) {
        return ENCODER.encodeToString(iri.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String id) {
        return new String(DECODER.decode(id), StandardCharsets.UTF_8);
    }
}
