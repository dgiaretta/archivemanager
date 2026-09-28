package info.oais.archive.manager.service.format;

/**
 * Turns a free-text field/node name (whatever the user typed -- "SIMPLE",
 * "NAXIS1", "temperature", even something with spaces) into a valid
 * lower_snake_case identifier, shared by every generator that needs one
 * (Kaitai's {@code id:}, a DFDL {@code xs:element name}, a drb-python
 * attribute name, a Java DRB field constant/class name -- the latter two
 * built on top of this via {@code DrbGenerator}'s own
 * {@code toPascalCase}/{@code toScreamingSnakeCase}) without duplicating the
 * same sanitization in each one.
 */
public final class FormatIdentifiers {

    private FormatIdentifiers() {
    }

    public static String snakeCase(String name) {
        if (name == null || name.isBlank()) {
            return "field";
        }
        String withUnderscores = name.strip()
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toLowerCase()
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (withUnderscores.isEmpty()) {
            return "field";
        }
        return Character.isDigit(withUnderscores.charAt(0)) ? "f_" + withUnderscores : withUnderscores;
    }
}
