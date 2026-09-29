package info.oais.archive.manager.web;

import info.oais.infomodel.structure.description.Semantics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Meaning" part of RepInfo Tools' edit forms, shared by the element
 * editor and the column editor (template fragment
 * {@code repinfo-tools/semantics-fields}): a {@link Semantics} as the text in
 * its inputs, and the inputs read back into one.
 */
public record SemanticsForm(String semanticName, String definition, String units, String unitsUri, String conceptUri,
                            String codes, String scale, String offset, String fillValue, String validMin,
                            String validMax) {

    public static SemanticsForm of(Semantics s) {
        String codes = String.join("\n", s.codes().entrySet().stream()
                .map(c -> c.getKey() + " = " + c.getValue()).toList());
        return new SemanticsForm(nz(s.semanticName()), nz(s.definition()), nz(s.units()),
                s.unitsUri() == null ? "" : s.unitsUri().toString(), s.conceptUri() == null ? "" : s.conceptUri().toString(),
                codes, plain(s.scale()), plain(s.offset()), nz(s.fillValue()), plain(s.validMin()), plain(s.validMax()));
    }

    /**
     * The semantics in a submitted form; see {@link Semantics} for what each part means.
     *
     * @throws IllegalArgumentException saying which input isn't valid, and why
     */
    public static Semantics parse(Map<String, String> form) {
        Map<String, String> codes = new LinkedHashMap<>();
        for (String line : form.getOrDefault("codes", "").split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int sep = line.indexOf('=') >= 0 ? line.indexOf('=') : line.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException("Write each coded value as 'value = meaning', e.g. '1 = housekeeping' (not '"
                        + line.strip() + "').");
            }
            codes.put(line.substring(0, sep).strip(), line.substring(sep + 1).strip());
        }
        return new Semantics(form.get("semanticName"), form.get("definition"), form.get("units"),
                uri(form.get("unitsUri"), "The units link"), uri(form.get("conceptUri"), "The concept link"), codes,
                decimal(form.get("scale"), "The scale"), decimal(form.get("offset"), "The offset"), form.get("fillValue"),
                decimal(form.get("validMin"), "The smallest valid value"), decimal(form.get("validMax"), "The largest valid value"));
    }

    private static java.net.URI uri(String text, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(text.strip());
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException(what + " must be a full web address, starting http:// or https://.");
            }
            return uri;
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(what + " isn't a valid web address.");
        }
    }

    private static java.math.BigDecimal decimal(String text, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number (not '" + text.strip() + "').");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String plain(java.math.BigDecimal d) {
        return d == null ? "" : d.toPlainString();
    }
}
