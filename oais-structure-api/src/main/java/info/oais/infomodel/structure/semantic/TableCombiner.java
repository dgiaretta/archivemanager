package info.oais.infomodel.structure.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import info.oais.infomodel.interfaces.utility.OaisIfTable;

/**
 * Combines two {@link OaisIfTable}s - typically the same {@link TableMapping}
 * (or {@link TableSemanticRepInfo}) applied to two different Digital Objects,
 * e.g. two files that share a format - into one {@code OaisIfTable}, the same
 * way {@link StructureNodeBackedTable} and friends give a read-only view
 * rather than a copy.
 *
 * <p>Two combinations are provided, matching the two ways rows from two
 * tables commonly relate to each other:</p>
 *
 * <ul>
 * <li>{@link #join}: rows are paired up positionally (first row of the left
 * table with first row of the right table, and so on), and each pair becomes
 * one wider row holding both tables' columns side by side. This is what lets
 * a column from one table be plotted against, compared with, or otherwise
 * read alongside a column from the other - e.g. with {@link XyScatterPanel} -
 * since both values then live in the same row of the same table.</li>
 * <li>{@link #union}: rows are stacked, the left table's rows followed by the
 * right table's, into one taller table with a leading column recording which
 * source table each row came from. This is for treating both files' data as
 * one combined dataset - for a combined row count, a combined CSV export, or
 * filtering/aggregating across both without caring which file a row
 * originated from.</li>
 * </ul>
 *
 * <p>Both are simple, general-purpose starting points, not the only ways to
 * combine two datasets. Other useful combinations that could be layered on
 * top of these same two {@code OaisIfTable}s, depending on the data: a
 * key-based join (pairing rows by matching value in a shared identifier
 * column, rather than by position) where the two datasets do not line up row
 * for row; a derived/computed column over a {@link #join} result (e.g. a
 * "difference" column between the two tables' corresponding values, for
 * comparing two versions of the same records); or summary statistics
 * (row counts, min/max/mean, correlation) computed across a {@link #join} or
 * {@link #union} result.</p>
 */
public final class TableCombiner {

	private TableCombiner() {
	}

	/**
	 * Pairs up {@code left} and {@code right} positionally - row 0 with row
	 * 0, row 1 with row 1, and so on - into one table whose columns are
	 * {@code left}'s columns (each renamed {@code leftPrefix + "." + name})
	 * followed by {@code right}'s columns (renamed the same way with
	 * {@code rightPrefix}). Its row count is {@code min(left.getRowCount(),
	 * right.getRowCount())}: a row on the longer side with no corresponding
	 * row on the shorter side has nothing to be paired with, so it is
	 * dropped rather than paired with a made-up value.
	 *
	 * @param left        the table contributing the first columns
	 * @param leftPrefix  prefix for {@code left}'s column names in the result
	 * @param right       the table contributing the last columns
	 * @param rightPrefix prefix for {@code right}'s column names in the result
	 * @return a read-only {@code OaisIfTable} with {@code left}'s and
	 *         {@code right}'s columns side by side
	 */
	public static OaisIfTable join(OaisIfTable left, String leftPrefix, OaisIfTable right, String rightPrefix) {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		Objects.requireNonNull(leftPrefix, "leftPrefix");
		Objects.requireNonNull(rightPrefix, "rightPrefix");

		int leftColumnCount = left.getColumnCount();
		int totalColumnCount = leftColumnCount + right.getColumnCount();
		List<String> names = new ArrayList<>(totalColumnCount);
		List<Class<?>> classes = new ArrayList<>(totalColumnCount);
		for (int c = 0; c < leftColumnCount; c++) {
			names.add(prefixed(leftPrefix, left.getColumnName(c)));
			classes.add(left.getColumnClass(c));
		}
		for (int c = 0; c < right.getColumnCount(); c++) {
			names.add(prefixed(rightPrefix, right.getColumnName(c)));
			classes.add(right.getColumnClass(c));
		}

		long rowCount = Math.min(left.getRowCount(), right.getRowCount());
		return new ComputedTable(rowCount, names, classes,
				(row, col) -> col < leftColumnCount ? left.getValueAt(row, col) : right.getValueAt(row, col - leftColumnCount));
	}

	/**
	 * Stacks {@code first}'s rows followed by {@code second}'s rows into one
	 * table with the same columns as {@code first} and {@code second} (which
	 * must declare the same number of columns, with the same
	 * {@link OaisIfTable#getColumnClass column classes} in the same order -
	 * column <em>names</em> are taken from {@code first} and are not required
	 * to match, since the same logical column can be named slightly
	 * differently by two different mappings), plus one leading {@code source}
	 * column holding {@code firstSource} for {@code first}'s rows and
	 * {@code secondSource} for {@code second}'s.
	 *
	 * @param first        the table contributing the earlier rows
	 * @param firstSource  the {@code source} value recorded for {@code first}'s rows
	 * @param second       the table contributing the later rows
	 * @param secondSource the {@code source} value recorded for {@code second}'s rows
	 * @return a read-only {@code OaisIfTable} with both tables' rows stacked,
	 *         tagged by origin
	 * @throws IllegalArgumentException if {@code first} and {@code second}
	 *                                  do not declare the same column count
	 *                                  and, positionally, the same column
	 *                                  classes
	 */
	public static OaisIfTable union(OaisIfTable first, String firstSource, OaisIfTable second, String secondSource) {
		Objects.requireNonNull(first, "first");
		Objects.requireNonNull(second, "second");
		Objects.requireNonNull(firstSource, "firstSource");
		Objects.requireNonNull(secondSource, "secondSource");

		int columnCount = first.getColumnCount();
		if (columnCount != second.getColumnCount()) {
			throw new IllegalArgumentException("Cannot union tables with different column counts: "
					+ columnCount + " vs. " + second.getColumnCount());
		}
		List<String> names = new ArrayList<>(columnCount + 1);
		List<Class<?>> classes = new ArrayList<>(columnCount + 1);
		names.add("source");
		classes.add(String.class);
		for (int c = 0; c < columnCount; c++) {
			if (!first.getColumnClass(c).equals(second.getColumnClass(c))) {
				throw new IllegalArgumentException("Cannot union tables: column " + c + " (" + first.getColumnName(c)
						+ ") is " + first.getColumnClass(c).getSimpleName() + " in the first table but "
						+ second.getColumnClass(c).getSimpleName() + " in the second");
			}
			names.add(first.getColumnName(c));
			classes.add(first.getColumnClass(c));
		}

		long firstRowCount = first.getRowCount();
		long rowCount = firstRowCount + second.getRowCount();
		return new ComputedTable(rowCount, names, classes, (row, col) -> {
			boolean fromFirst = row < firstRowCount;
			if (col == 0) {
				return fromFirst ? firstSource : secondSource;
			}
			return fromFirst ? first.getValueAt(row, col - 1) : second.getValueAt(row - firstRowCount, col - 1);
		});
	}

	private static String prefixed(String prefix, String name) {
		return prefix.isEmpty() ? name : prefix + "." + name;
	}

	@FunctionalInterface
	private interface CellValueFunction {
		Object valueAt(long row, int column);
	}

	/**
	 * A read-only {@link OaisIfTable} backed by a plain value function rather
	 * than a {@link info.oais.infomodel.structure.StructureNode} tree - the
	 * {@link #join}/{@link #union} counterpart to
	 * {@link StructureNodeBackedTable}, which is why it rejects mutation the
	 * same way.
	 */
	private static final class ComputedTable implements OaisIfTable {

		private final long rowCount;
		private final List<String> columnNames;
		private final List<Class<?>> columnClasses;
		private final CellValueFunction valueFunction;

		ComputedTable(long rowCount, List<String> columnNames, List<Class<?>> columnClasses,
				CellValueFunction valueFunction) {
			this.rowCount = rowCount;
			this.columnNames = List.copyOf(columnNames);
			this.columnClasses = List.copyOf(columnClasses);
			this.valueFunction = valueFunction;
		}

		@Override
		public long getRowCount() {
			return rowCount;
		}

		@Override
		public int getColumnCount() {
			return columnNames.size();
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnNames.get(columnIndex);
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnClasses.get(columnIndex);
		}

		@Override
		public Object getValueAt(long rowIndex, int columnIndex) {
			return valueFunction.valueAt(rowIndex, columnIndex);
		}

		@Override
		public void addColumn(String colName, Class<?> colType) {
			throw new UnsupportedOperationException(
					"This table is a computed, read-only combination of two other tables - its columns are fixed");
		}

		@Override
		public void setValueAt(Object obj, long rowIndex, int columnIndex) {
			throw new UnsupportedOperationException(
					"This table is a computed, read-only combination of two other tables - edit the source tables instead");
		}
	}
}
