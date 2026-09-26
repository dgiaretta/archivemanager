package info.oais.infomodel.structure.semantic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JComponent;

import info.oais.infomodel.interfaces.utility.OaisIfTable;

/**
 * Plots two numeric columns of one {@link OaisIfTable} against each other as
 * a plain XY scatter chart - the visual counterpart to
 * {@link OaisIfTableModel} (a {@code JTable} view of a whole table), for the
 * common case of wanting to see whether two columns correlate rather than
 * read them cell by cell.
 *
 * <p>Both columns must come from the <em>same</em> table, since each point
 * needs a shared row to pair an x value with a y value; to plot a column
 * from one Digital Object's table against a column from a different Digital
 * Object's table - e.g. the same field decoded from two different files -
 * first bring them into one table with {@link TableCombiner#join}, then plot
 * two of its columns here. See that class's Javadoc for why a join, rather
 * than the two tables directly, is what makes this possible.</p>
 *
 * <p>Deliberately has no third-party charting dependency, consistent with
 * this project's adapters (see the root README's notes on keeping
 * engine-specific dependencies confined to the module that needs them): this
 * is plain {@link Graphics2D}, matching the rest of this demo-facing Swing
 * code ({@link OaisIfTableModel}, {@code TableModelCsv}).</p>
 */
public final class XyScatterPanel extends JComponent {

	private static final long serialVersionUID = 1L;
	private static final int LEFT_MARGIN = 56;
	private static final int BOTTOM_MARGIN = 40;
	private static final int TOP_MARGIN = 16;
	private static final int RIGHT_MARGIN = 16;
	private static final int POINT_RADIUS = 4;

	private final String xLabel;
	private final String yLabel;
	private final List<double[]> points;

	/**
	 * @param table    the table to read both columns from
	 * @param xColumn  index of the column to plot on the x axis; must have a
	 *                 {@link OaisIfTable#getColumnClass} assignable to {@link Number}
	 * @param yColumn  index of the column to plot on the y axis; same
	 *                 constraint as {@code xColumn}
	 * @throws IllegalArgumentException if either column is not numeric
	 */
	public XyScatterPanel(OaisIfTable table, int xColumn, int yColumn) {
		Objects.requireNonNull(table, "table");
		requireNumeric(table, xColumn);
		requireNumeric(table, yColumn);

		this.xLabel = table.getColumnName(xColumn);
		this.yLabel = table.getColumnName(yColumn);
		this.points = new ArrayList<>();
		for (long row = 0; row < table.getRowCount(); row++) {
			Object xValue = table.getValueAt(row, xColumn);
			Object yValue = table.getValueAt(row, yColumn);
			if (xValue instanceof Number && yValue instanceof Number) {
				points.add(new double[] { ((Number) xValue).doubleValue(), ((Number) yValue).doubleValue() });
			}
		}
		setPreferredSize(new Dimension(320, 240));
		setBackground(Color.WHITE);
	}

	private static void requireNumeric(OaisIfTable table, int column) {
		Class<?> columnClass = table.getColumnClass(column);
		if (!Number.class.isAssignableFrom(columnClass)) {
			throw new IllegalArgumentException("Column \"" + table.getColumnName(column) + "\" is "
					+ columnClass.getSimpleName() + ", not numeric - cannot be plotted on an XY axis");
		}
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getBackground());
			g2.fillRect(0, 0, getWidth(), getHeight());

			int plotLeft = LEFT_MARGIN;
			int plotRight = getWidth() - RIGHT_MARGIN;
			int plotTop = TOP_MARGIN;
			int plotBottom = getHeight() - BOTTOM_MARGIN;
			if (plotRight <= plotLeft || plotBottom <= plotTop) {
				return;
			}

			g2.setColor(Color.DARK_GRAY);
			g2.draw(new Line2D.Double(plotLeft, plotBottom, plotRight, plotBottom));
			g2.draw(new Line2D.Double(plotLeft, plotTop, plotLeft, plotBottom));

			FontMetrics metrics = g2.getFontMetrics();
			g2.drawString(xLabel, (plotLeft + plotRight) / 2 - metrics.stringWidth(xLabel) / 2, getHeight() - 6);
			drawVerticalLabel(g2, yLabel, plotTop, plotBottom, metrics);

			if (points.isEmpty()) {
				return;
			}

			double[] xRange = axisRange(0);
			double[] yRange = axisRange(1);

			g2.setColor(Color.GRAY);
			g2.drawString(format(xRange[0]), plotLeft, plotBottom + 14);
			String maxXText = format(xRange[1]);
			g2.drawString(maxXText, plotRight - metrics.stringWidth(maxXText), plotBottom + 14);
			g2.drawString(format(yRange[1]), plotLeft - metrics.stringWidth(format(yRange[1])) - 6, plotTop + 10);
			g2.drawString(format(yRange[0]), plotLeft - metrics.stringWidth(format(yRange[0])) - 6, plotBottom);

			g2.setColor(new Color(31, 119, 180));
			for (double[] point : points) {
				double px = plotLeft + normalize(point[0], xRange) * (plotRight - plotLeft);
				double py = plotBottom - normalize(point[1], yRange) * (plotBottom - plotTop);
				g2.fill(new Ellipse2D.Double(px - POINT_RADIUS, py - POINT_RADIUS, POINT_RADIUS * 2.0, POINT_RADIUS * 2.0));
			}
		} finally {
			g2.dispose();
		}
	}

	private static void drawVerticalLabel(Graphics2D g2, String label, int plotTop, int plotBottom,
			FontMetrics metrics) {
		Graphics2D rotated = (Graphics2D) g2.create();
		try {
			rotated.rotate(-Math.PI / 2);
			rotated.drawString(label, -(plotTop + plotBottom) / 2 - metrics.stringWidth(label) / 2, 14);
		} finally {
			rotated.dispose();
		}
	}

	/** Returns {@code [min, max]} for coordinate {@code axis} (0=x, 1=y), padded by 10% so points are not drawn on the axis itself. */
	private double[] axisRange(int axis) {
		double min = points.stream().mapToDouble(p -> p[axis]).min().orElse(0);
		double max = points.stream().mapToDouble(p -> p[axis]).max().orElse(1);
		double range = max - min == 0 ? Math.max(Math.abs(max), 1) : max - min;
		double padding = range * 0.1;
		return new double[] { min - padding, max + padding };
	}

	private static double normalize(double value, double[] range) {
		return (value - range[0]) / (range[1] - range[0]);
	}

	private static String format(double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return String.valueOf((long) value);
		}
		return String.format("%.2f", value);
	}
}
