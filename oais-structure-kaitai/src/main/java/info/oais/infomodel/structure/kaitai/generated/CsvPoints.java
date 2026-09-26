package info.oais.infomodel.structure.kaitai.generated;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kaitai.struct.KaitaiStruct;
import io.kaitai.struct.KaitaiStream;

/**
 * Hand-written stand-in for the Java class {@code ksc --target java
 * --java-package info.oais.infomodel.structure.kaitai.generated} generates
 * from {@code src/main/ksy/csv_points.ksy} - see {@link Point2d}'s Javadoc
 * for why this project hand-writes these rather than running the real
 * compiler, and for the {@code _debug} map convention {@link Row} follows
 * below.
 *
 * <p>Unlike {@link Point2d} (one fixed-width record), this exercises Kaitai
 * Struct's {@code repeat: eos} - "keep reading {@code row}s until end of
 * stream" - which is why {@link #rows()} returns a {@link List}: per
 * {@link info.oais.infomodel.structure.kaitai.KaitaiReflectiveStructureNode}'s
 * Javadoc, a {@code List}-returning accessor is what makes it report that
 * field as {@link info.oais.infomodel.structure.StructureNodeKind#ARRAY}
 * rather than {@link info.oais.infomodel.structure.StructureNodeKind#COMPOSITE}.
 * No other adapter code change was needed to support a repeated Kaitai
 * format - the reflective bridge already handled {@code List} fields
 * generically, {@link Row} just did not exist to exercise it yet.</p>
 */
public class CsvPoints extends KaitaiStruct {

	private final List<Row> rows = new ArrayList<>();

	public CsvPoints(KaitaiStream io) {
		super(io);
		_read();
	}

	private void _read() {
		while (!this._io.isEof()) {
			rows.add(new Row(this._io));
		}
	}

	public List<Row> rows() {
		return rows;
	}

	/**
	 * One {@code x,y,label} line. Kept as a static inner class, as Kaitai
	 * Struct's own Java target generates for a named {@code type:} - see
	 * {@code KaitaiReflectiveStructureNode}'s Javadoc on why a nested
	 * {@link KaitaiStruct} field is walked recursively as its own COMPOSITE
	 * node.
	 */
	public static class Row extends KaitaiStruct {

		private final String x;
		private final String y;
		private final String label;

		public final Map<String, Object> _debug = new HashMap<>();

		public Row(KaitaiStream io) {
			super(io);

			Map<String, Object> debugX = new HashMap<>();
			debugX.put("start", (long) this._io.pos());
			this.x = readTerminated(io, (byte) ',', true);
			debugX.put("end", (long) this._io.pos());
			_debug.put("x", debugX);

			Map<String, Object> debugY = new HashMap<>();
			debugY.put("start", (long) this._io.pos());
			this.y = readTerminated(io, (byte) ',', true);
			debugY.put("end", (long) this._io.pos());
			_debug.put("y", debugY);

			Map<String, Object> debugLabel = new HashMap<>();
			debugLabel.put("start", (long) this._io.pos());
			this.label = readTerminated(io, (byte) '\n', false);
			debugLabel.put("end", (long) this._io.pos());
			_debug.put("label", debugLabel);
		}

		public String x() {
			return x;
		}

		public String y() {
			return y;
		}

		public String label() {
			return label;
		}

		/**
		 * Exactly what the real compiler generates for a {@code type: str,
		 * terminator: ..., encoding: ASCII} field: {@code readBytesTerm}
		 * with {@code includeTerm=false, consumeTerm=true}, plus, for
		 * {@code label} only, {@code eosError=false} (matching
		 * {@code csv_points.ksy}'s {@code eos-error: false} on that field) so
		 * a final line with no trailing newline still parses.
		 */
		private static String readTerminated(KaitaiStream io, byte terminator, boolean eosError) {
			byte[] bytes = io.readBytesTerm(terminator, false, true, eosError);
			return new String(bytes, StandardCharsets.US_ASCII);
		}
	}
}
