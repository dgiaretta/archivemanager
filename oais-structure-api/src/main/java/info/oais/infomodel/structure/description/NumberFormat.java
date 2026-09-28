package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.Objects;

/**
 * How a number is written in delimited text, when it isn't plain decimal
 * digits: a pattern in the style of DFDL's {@code textNumberPattern} (and
 * Java's {@code DecimalFormat}), e.g. {@code #,##0.00}, with the characters
 * the data uses for the decimal point and for grouping thousands.
 *
 * @param decimalSeparator  e.g. {@code "."} or {@code ","}
 * @param groupingSeparator e.g. {@code ","}, {@code "."} or {@code " "}; null if the data never groups
 */
public record NumberFormat(String pattern, String decimalSeparator, String groupingSeparator)
		implements Serializable {

	public NumberFormat {
		Objects.requireNonNull(pattern, "pattern");
		decimalSeparator = decimalSeparator == null || decimalSeparator.isEmpty() ? "." : decimalSeparator;
		groupingSeparator = groupingSeparator == null || groupingSeparator.isEmpty() ? null : groupingSeparator;
	}
}
