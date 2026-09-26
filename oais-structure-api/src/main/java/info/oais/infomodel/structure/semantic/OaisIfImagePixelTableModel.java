package info.oais.infomodel.structure.semantic;

import javax.swing.table.AbstractTableModel;

import info.oais.infomodel.interfaces.utility.OaisIfImage;

/**
 * Adapts any {@link OaisIfImage} - a {@link StructureNodeBackedImage} built
 * from an {@link ImageViewSpecification} or a hand-built {@link ImageMapping},
 * or any other implementation - to Swing's table model API, one pixel per
 * cell, so it can be displayed (and
 * edited - see below) in a real {@link javax.swing.JTable} without writing
 * per-application adapter code. See {@link OaisIfTableModel}, which this
 * otherwise mirrors; the difference is entirely in how edits are held, and
 * is explained below.
 *
 * <p><b>Snapshot, not overlay:</b> unlike {@link OaisIfTableModel} - which
 * layers a sparse overlay of edited cells over the wrapped {@code OaisIfTable},
 * since that table can answer for any individual cell on demand -
 * {@link OaisIfImage#getPixelValues()} has no per-cell getter: it always
 * rebuilds and returns the <em>entire</em> pixel grid. Calling it once per
 * cell read would therefore be quadratic in the number of pixels, so this
 * model instead calls it exactly once, at construction, into a local mutable
 * array snapshot, and every read and write after that touches only the
 * snapshot. As with {@link OaisIfTableModel}, an edit is <em>never</em>
 * written through to the wrapped {@code OaisIfImage} itself - construct a
 * fresh {@code OaisIfImagePixelTableModel} (e.g. by re-running the owning
 * {@code ExecutableSemanticRepInfo#apply}) to discard accumulated edits and
 * go back to the wrapped image's own values.</p>
 *
 * <p>Every column shares the image's one {@link OaisIfImage#getPixelClass()};
 * columns are named by their zero-based index, since a pixel grid has no
 * column names of its own.</p>
 */
public final class OaisIfImagePixelTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private final Class<?> pixelClass;
	private final boolean editable;
	private final Object[][] pixels;

	public OaisIfImagePixelTableModel(OaisIfImage image) {
		this(image, true);
	}

	/**
	 * @param image    the image to adapt; its pixel grid is copied out once,
	 *                 immediately, via {@link OaisIfImage#getPixelValues()}
	 * @param editable whether {@link #isCellEditable} reports cells as
	 *                 editable at all
	 */
	public OaisIfImagePixelTableModel(OaisIfImage image, boolean editable) {
		this.pixelClass = image.getPixelClass();
		this.editable = editable;
		Object[][] source = image.getPixelValues();
		this.pixels = new Object[source.length][];
		for (int r = 0; r < source.length; r++) {
			this.pixels[r] = source[r].clone();
		}
	}

	@Override
	public int getRowCount() {
		return pixels.length;
	}

	@Override
	public int getColumnCount() {
		return pixels.length == 0 ? 0 : pixels[0].length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return Integer.toString(columnIndex);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return pixelClass;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return editable;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return pixels[rowIndex][columnIndex];
	}

	/**
	 * Records an edit directly into this model's local pixel snapshot - see
	 * this class's Javadoc on "Snapshot, not overlay". A no-op if this model
	 * was built with {@code editable=false}; {@link #isCellEditable} already
	 * tells Swing not to invoke this in that case, so this check is only a
	 * defensive backstop against a caller invoking it directly.
	 */
	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if (!editable) {
			return;
		}
		pixels[rowIndex][columnIndex] = ValueCoercion.coerce(aValue, pixelClass);
		fireTableCellUpdated(rowIndex, columnIndex);
	}
}
