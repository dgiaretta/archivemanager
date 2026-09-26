package info.oais.infomodel.structure.semantic;

import java.io.IOException;
import java.io.Writer;

import javax.swing.table.TableModel;

/**
 * Writes any Swing {@link TableModel} out as CSV (RFC 4180-style: a field
 * containing a comma, a double quote or a line break is quoted, with
 * embedded quotes doubled; rows are terminated {@code \r\n}).
 *
 * <p>Deliberately written against plain {@link TableModel}, not
 * {@link OaisIfTableModel} specifically or
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTable} directly: the
 * point is to capture whatever a user has actually edited in a
 * {@link javax.swing.JTable}, and {@link OaisIfTableModel} deliberately never
 * writes an edit back to the {@code OaisIfTable} it wraps (see that class's
 * Javadoc on "Editing") - reading straight from the wrapped table would miss
 * those edits entirely. Reading through the {@code TableModel} instead - the
 * same object the {@code JTable} itself renders from - always reflects
 * exactly what is on screen.</p>
 */
public final class TableModelCsv {

	private TableModelCsv() {
	}

	/**
	 * Writes {@code model}'s current contents (column names as a header row,
	 * then one row per {@code model} row) to {@code out} as CSV. Does not
	 * close {@code out}.
	 *
	 * @param model the table model to write, typically mid-edit in a live JTable
	 * @param out   where to write the CSV text
	 * @throws IOException if writing to {@code out} fails
	 */
	public static void write(TableModel model, Writer out) throws IOException {
		int columnCount = model.getColumnCount();
		for (int c = 0; c < columnCount; c++) {
			if (c > 0) {
				out.write(',');
			}
			out.write(escape(model.getColumnName(c)));
		}
		out.write("\r\n");

		int rowCount = model.getRowCount();
		for (int r = 0; r < rowCount; r++) {
			for (int c = 0; c < columnCount; c++) {
				if (c > 0) {
					out.write(',');
				}
				Object value = model.getValueAt(r, c);
				out.write(escape(value == null ? "" : value.toString()));
			}
			out.write("\r\n");
		}
	}

	private static String escape(String field) {
		boolean needsQuoting = field.indexOf(',') >= 0 || field.indexOf('"') >= 0
				|| field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0;
		if (!needsQuoting) {
			return field;
		}
		return '"' + field.replace("\"", "\"\"") + '"';
	}
}
