package info.oais.infomodel.structure.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import info.oais.infomodel.implementation.utility.OaisIfTimeStampRefImpl;
import info.oais.infomodel.interfaces.utility.OaisIfTimeStamp;
import info.oais.infomodel.structure.StructureNode;

/**
 * Declares how to see some {@link StructureNode} subtree as a time series:
 * like {@link TableMapping}, but column zero (and, if {@code eventEnd} is
 * given, column one) is fixed to produce {@link OaisIfTimeStamp} values, per
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTimeSeries}'s
 * documented contract. See {@link TimeSeriesSemanticRepInfo}.
 *
 * @param rowSelector  see {@link TableMapping#rowSelector()}
 * @param eventStart   column zero: must produce {@link OaisIfTimeStamp} values
 * @param eventEnd     column one, or {@code null} if this series has no
 *                     recorded event end - must produce
 *                     {@link OaisIfTimeStamp} values if given
 * @param otherColumns the remaining columns, in order
 */
public record TimeSeriesMapping(Function<StructureNode, List<StructureNode>> rowSelector,
		ColumnMapping eventStart,
		ColumnMapping eventEnd,
		List<ColumnMapping> otherColumns) {

	public TimeSeriesMapping {
		otherColumns = otherColumns == null ? List.of() : List.copyOf(otherColumns);
	}

	/**
	 * Convenience: an {@code eventStart}/{@code eventEnd} {@link ColumnMapping}
	 * that reads a row's named child as epoch milliseconds (a {@link Number})
	 * and wraps it in an {@link OaisIfTimeStampRefImpl}.
	 *
	 * @param columnName the column name, and the row's child name to read
	 *                   the epoch-millisecond value from
	 * @return a mapping producing an {@link OaisIfTimeStamp} for each row
	 */
	public static ColumnMapping epochMillisColumn(String columnName) {
		return new ColumnMapping(columnName, OaisIfTimeStamp.class, row -> {
			Object value = row.child(columnName).flatMap(StructureNode::getValue).orElse(null);
			if (!(value instanceof Number number)) {
				return null;
			}
			OaisIfTimeStampRefImpl timestamp = new OaisIfTimeStampRefImpl();
			timestamp.setTime(number.longValue());
			return timestamp;
		});
	}

	TableMapping toTableMapping() {
		List<ColumnMapping> columns = new ArrayList<>();
		columns.add(eventStart);
		if (eventEnd != null) {
			columns.add(eventEnd);
		}
		columns.addAll(otherColumns);
		return new TableMapping(rowSelector, columns);
	}
}
