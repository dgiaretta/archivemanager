package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.RoundTrip;

/**
 * What writing a sample back with a description produced, in RepInfo Tools:
 * the written bytes and how they compare with the sample, or why writing
 * failed.
 *
 * @param written   the written bytes; null on failure
 * @param roundTrip the written bytes compared with the sample (or with what they're meant to match); null on failure
 * @param error     why writing failed, or null
 * @param note      something to know when reading the comparison, or null
 */
public record WriteBackResult(byte[] written, RoundTrip roundTrip, String error, String note) {

    public static WriteBackResult failure(String error) {
        return new WriteBackResult(null, null, error, null);
    }

    public static WriteBackResult of(byte[] original, byte[] written, String note) {
        return new WriteBackResult(written, RoundTrip.compare(original, written), null, note);
    }

    public boolean ok() {
        return error == null;
    }

    /** An exception's message, with its cause's if that adds anything -- the engines put their diagnostics there. */
    static String message(Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        if (e.getCause() != null && e.getCause().getMessage() != null && !message.contains(e.getCause().getMessage())) {
            message += "\n" + e.getCause().getMessage();
        }
        return message;
    }
}
