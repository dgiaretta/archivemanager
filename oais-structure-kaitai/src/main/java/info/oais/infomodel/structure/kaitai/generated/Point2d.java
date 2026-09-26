package info.oais.infomodel.structure.kaitai.generated;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.kaitai.struct.KaitaiStruct;
import io.kaitai.struct.KaitaiStream;

/**
 * Hand-written stand-in for the Java class {@code ksc --target java
 * --java-package info.oais.infomodel.structure.kaitai.generated} generates
 * from {@code src/main/ksy/point2d.ksy}.
 *
 * <p><b>This file exists only so {@code oais-structure-kaitai} has something
 * concrete to compile and test against without needing to run the Kaitai
 * Struct compiler as part of this project's build (see the module and root
 * READMEs for why - no outbound access to fetch/run the compiler toolchain
 * was available in the environment this project was authored in).</b>
 * {@link info.oais.infomodel.structure.kaitai.KaitaiReflectiveStructureNode},
 * the actual adapter logic, does not know or care that this class was
 * hand-written rather than generated: it only relies on the conventions
 * Kaitai's Java target documents (public no-argument getters named after
 * each field, in camelCase, with no {@code get} prefix; nested types as
 * static inner classes; repeated fields as {@code java.util.List}), which
 * this class follows deliberately. Regenerate this file with the real
 * compiler and delete this notice once you have it wired into your build -
 * use {@code ksc --debug} when you do, to keep the {@code _debug} map below
 * (see next paragraph).</p>
 *
 * <p><b>{@code _debug}:</b> also hand-populated, following the shape the
 * real compiler emits when invoked with {@code --debug} - a public
 * {@code Map<String, Object>} keyed by field id, each entry a map with
 * "start"/"end" byte offsets - which
 * {@link info.oais.infomodel.structure.kaitai.KaitaiReflectiveStructureNode#getSourceRange()}
 * reads by reflection. This project could not run the real compiler to
 * confirm the exact key naming convention it uses (Java accessor name vs.
 * the original {@code .ksy} snake_case id), so that class tries both; see
 * its Javadoc.</p>
 */
public class Point2d extends KaitaiStruct {

	private int x;
	private int y;
	private int labelLen;
	private String label;

	public final Map<String, Object> _debug = new HashMap<>();

	public Point2d(KaitaiStream io) {
		super(io);
		_read();
	}

	private void _read() {
		Map<String, Object> debugX = new HashMap<>();
		debugX.put("start", (long) this._io.pos());
		this.x = this._io.readS4be();
		debugX.put("end", (long) this._io.pos());
		_debug.put("x", debugX);

		Map<String, Object> debugY = new HashMap<>();
		debugY.put("start", (long) this._io.pos());
		this.y = this._io.readS4be();
		debugY.put("end", (long) this._io.pos());
		_debug.put("y", debugY);

		Map<String, Object> debugLabelLen = new HashMap<>();
		debugLabelLen.put("start", (long) this._io.pos());
		this.labelLen = this._io.readU1();
		debugLabelLen.put("end", (long) this._io.pos());
		_debug.put("labelLen", debugLabelLen);

		Map<String, Object> debugLabel = new HashMap<>();
		debugLabel.put("start", (long) this._io.pos());
		this.label = new String(this._io.readBytes(this.labelLen), StandardCharsets.US_ASCII);
		debugLabel.put("end", (long) this._io.pos());
		_debug.put("label", debugLabel);
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int labelLen() {
		return labelLen;
	}

	public String label() {
		return label;
	}
}
