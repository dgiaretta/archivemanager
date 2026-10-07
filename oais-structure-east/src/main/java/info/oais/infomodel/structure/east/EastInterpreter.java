package info.oais.infomodel.structure.east;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.east.EastParser.Agg;
import info.oais.infomodel.structure.east.EastParser.ArrTy;
import info.oais.infomodel.structure.east.EastParser.Bin;
import info.oais.infomodel.structure.east.EastParser.Call;
import info.oais.infomodel.structure.east.EastParser.Choice;
import info.oais.infomodel.structure.east.EastParser.Chr;
import info.oais.infomodel.structure.east.EastParser.Comp;
import info.oais.infomodel.structure.east.EastParser.Const;
import info.oais.infomodel.structure.east.EastParser.Discr;
import info.oais.infomodel.structure.east.EastParser.EnumTy;
import info.oais.infomodel.structure.east.EastParser.Index;
import info.oais.infomodel.structure.east.EastParser.IntTy;
import info.oais.infomodel.structure.east.EastParser.Name;
import info.oais.infomodel.structure.east.EastParser.Node;
import info.oais.infomodel.structure.east.EastParser.Num;
import info.oais.infomodel.structure.east.EastParser.Pkg;
import info.oais.infomodel.structure.east.EastParser.RealTy;
import info.oais.infomodel.structure.east.EastParser.RecTy;
import info.oais.infomodel.structure.east.EastParser.Str;
import info.oais.infomodel.structure.east.EastParser.SubTy;
import info.oais.infomodel.structure.east.EastParser.Ty;
import info.oais.infomodel.structure.east.EastParser.Un;
import info.oais.infomodel.structure.east.EastParser.Var;
import info.oais.infomodel.structure.east.EastParser.Variant;
import info.oais.infomodel.structure.east.EastParser.When;

/**
 * Interprets data directly with an EAST Data Description Record (CCSDS
 * 644.0-B-3), giving a tree of {@link StructureNode}s: one composite node per
 * record, an array node per array or repetition, and a leaf per value.
 *
 * <p>It follows the description itself rather than the engine-neutral model
 * {@link EastReader} builds, so it reads everything EAST can say: markers
 * (an element repeated until a value is found), components placed by record
 * representation clauses, bit fields stored least significant bit first,
 * integers whose bits are in several subfields, sign-and-magnitude and
 * ones'-complement integers, reals in each convention of CCSDS 646.0-G-1
 * (IEEE 754, VAX, MIL-STD-1750A, CDC NOS-VE and NOS-BE, IBM), numbers and
 * enumerations in ASCII, and virtual discriminants computed with any EAST
 * operator ({@code **}, {@code mod}, {@code cos}, {@code is_odd},
 * {@code !}...) from values anywhere in the data read so far, named by their
 * EAST paths.</p>
 *
 * <p>Names are given in lower case, as EAST names aren't case-sensitive. A
 * binary enumeration's leaf holds its code, with its literal as the
 * {@code meaning} attribute; an ASCII one holds its text. Unless an EOF
 * marker ends the last variable's repetition, the description is applied
 * repeatedly to the whole of the data, each time as a {@code set} node.</p>
 */
final class EastInterpreter {

	private final Pkg logical;
	private final EastPhysical physical;

	EastInterpreter(String text) {
		EastParser.Description d = EastParser.parse(text);
		this.logical = d.logical();
		EastParser.predefine(logical);
		this.physical = new EastPhysical(d.physical());
		if (logical.vars.isEmpty()) {
			throw new EastException(0, "The logical package '" + logical.raw + "' declares no variables, "
					+ "so it doesn't describe any data");
		}
		java.util.Set<String> checked = new java.util.HashSet<>();
		for (Var v : logical.vars) {
			check(v.type(), v.line(), checked);
		}
		for (Const marker : logical.markers.values()) {
			check(marker.type(), marker.line(), checked);
		}
	}

	/** Checks that a type, and every type it's made of, is declared. */
	private void check(String name, int line, java.util.Set<String> checked) {
		if (!checked.add(name)) {
			return;
		}
		Ty t = logical.types.get(name);
		if (t == null) {
			throw new EastException(line, "The type '" + name.toUpperCase(Locale.ROOT) + "' isn't declared");
		}
		if (t instanceof SubTy s) {
			check(s.base, s.line, checked);
		} else if (t instanceof ArrTy a) {
			check(a.element, a.line, checked);
			for (Index index : a.indices) {
				if (index.typeName() != null && !index.typeName().equals("positive")) {
					check(index.typeName(), a.line, checked);
				}
			}
		} else if (t instanceof RecTy r) {
			for (Discr d : r.discriminants) {
				check(d.type(), d.line(), checked);
			}
			for (Comp c : r.comps) {
				check(c.type(), c.line(), checked);
			}
			if (r.variant != null) {
				checkVariant(r.variant, checked);
			}
		}
	}

	private void checkVariant(Variant v, java.util.Set<String> checked) {
		for (When w : v.whens()) {
			for (Comp c : w.comps()) {
				check(c.type(), c.line(), checked);
			}
			if (w.nested() != null) {
				checkVariant(w.nested(), checked);
			}
		}
	}

	/** The name of the root node: the logical package's, in lower case. */
	String rootName() {
		return logical.name;
	}

	StructureNode decode(byte[] data) {
		return new Run(data).run();
	}

	// ------------------------------------------------------------------ types

	/** A type with its subtypes' constraints applied. */
	private record View(Ty base, List<Index> constraint, Node lo, Node hi) {
	}

	private Ty type(String name) {
		Ty t = logical.types.get(name);
		if (t == null) {
			throw new EastException(0, "The type '" + name.toUpperCase(Locale.ROOT) + "' isn't declared");
		}
		return t;
	}

	private View view(String name) {
		Ty t = type(name);
		List<Index> constraint = null;
		Node lo = null;
		Node hi = null;
		for (int i = 0; t instanceof SubTy s; i++) {
			if (i > 50) {
				throw new EastException(s.line, "The subtypes of '" + name + "' go round in a circle");
			}
			if (constraint == null) {
				constraint = s.constraint;
			}
			if (lo == null) {
				lo = s.lo;
				hi = s.hi;
			}
			t = type(s.base);
		}
		return new View(t, constraint, lo, hi);
	}

	// ------------------------------------------------------------------ one decoding

	/** The values of the record being read, by name: its stored and virtual discriminants and its components. */
	private final class Frame {
		final RecTy type;
		final String path;
		final Map<String, Object> locals = new HashMap<>();
		final Map<String, Node> virtuals = new HashMap<>();

		Frame(RecTy type, String path) {
			this.type = type;
			this.path = path;
		}
	}

	private final class Run {
		private final byte[] data;
		private final long end;
		private long pos;
		/** Every value read so far, by EAST path; the latest when a path is read more than once. */
		private final Map<String, Object> values = new HashMap<>();

		Run(byte[] data) {
			this.data = data;
			this.end = data.length * 8L;
		}

		StructureNode run() {
			DefaultStructureNode.Builder root = DefaultStructureNode.builder(logical.name, StructureNodeKind.COMPOSITE)
					.typeName(logical.name);
			if (logical.untilEof) {
				variables(root);
			} else {
				int n = 0;
				while (pos < end) {
					long start = pos;
					DefaultStructureNode.Builder set = DefaultStructureNode.builder("set", StructureNodeKind.COMPOSITE);
					variables(set);
					if (pos == start) {
						throw new StructureInterpretationException("A set of the data takes no bits, so it can't be "
								+ "repeated to the end of the data");
					}
					root.addChild(set.sourceRange(new ByteRange(start, pos - start)).build());
					n++;
				}
			}
			return root.sourceRange(new ByteRange(0, pos)).build();
		}

		private void variables(DefaultStructureNode.Builder parent) {
			List<Var> vars = logical.vars;
			for (int i = 0; i < vars.size(); i++) {
				Var v = vars.get(i);
				Const marker = logical.markers.get(v.name());
				boolean last = i == vars.size() - 1;
				if (marker != null || (logical.untilEof && last)) {
					parent.addChild(repeated(v.name(), v.type(), v.constraint(), v.name(), null,
							marker, logical.untilEof && last && marker == null));
				} else {
					parent.addChild(object(v.name(), v.type(), v.constraint(), v.name(), null, null));
				}
			}
		}

		// -------------------------------------------------- elements

		/**
		 * Reads one object of a type at the current position.
		 *
		 * @param frame the record it's a component of, for its discriminants; null for a variable
		 * @param width the bits a record representation clause gives it, if any
		 */
		private StructureNode object(String name, String typeName, List<Index> constraint, String path, Frame frame,
				Long width) {
			View v = view(typeName);
			Ty t = v.base();
			if (t instanceof RecTy r) {
				return record(name, r, path);
			}
			if (t instanceof ArrTy a) {
				List<Index> indices = constraint != null ? constraint : v.constraint() != null ? v.constraint()
						: a.indices;
				if (indices.stream().anyMatch(Index::unconstrained)) {
					throw new StructureInterpretationException("'" + path.toUpperCase(Locale.ROOT)
							+ "' needs the number of elements of " + typeName.toUpperCase(Locale.ROOT));
				}
				return array(name, a, indices, path, frame);
			}
			long start = pos;
			Leaf leaf = scalar(typeName, v, path, width);
			values.put(path, leaf.envValue());
			if (frame != null) {
				frame.locals.put(name, leaf.envValue());
			}
			DefaultStructureNode.Builder b = DefaultStructureNode.builder(name, StructureNodeKind.LEAF)
					.value(leaf.value()).typeName(typeName).sourceRange(new ByteRange(start, pos - start));
			leaf.attributes().forEach(b::attribute);
			return b.build();
		}

		/** An object repeated until a marker is found, or to the end of the data. */
		private StructureNode repeated(String name, String typeName, List<Index> constraint, String path, Frame frame,
				Const marker, boolean toEnd) {
			long start = pos;
			DefaultStructureNode.Builder array = DefaultStructureNode.builder(name, StructureNodeKind.ARRAY)
					.typeName(typeName);
			while (true) {
				if (toEnd && pos >= end) {
					break;
				}
				if (marker != null) {
					if (pos >= end) {
						throw new StructureInterpretationException("The data ends before the marker that ends the "
								+ "repetition of '" + path.toUpperCase(Locale.ROOT) + "'");
					}
					Long markerBits = markerAt(marker);
					if (markerBits != null) {
						pos += markerBits;
						array.attribute("marker", markerText(marker));
						break;
					}
				}
				long before = pos;
				array.addChild(object(name, typeName, constraint, path, frame, null));
				if (pos == before) {
					throw new StructureInterpretationException("'" + path.toUpperCase(Locale.ROOT)
							+ "' takes no bits, so it can't be repeated");
				}
			}
			return array.sourceRange(new ByteRange(start, pos - start)).build();
		}

		// -------------------------------------------------- markers

		/** How many bits the marker takes if it's at the current position; null if it isn't there. */
		private Long markerAt(Const marker) {
			Node value = marker.value();
			byte[] bytes = null;
			if (value instanceof Str s) {
				bytes = s.value().getBytes(StandardCharsets.ISO_8859_1);
			} else if (value instanceof Chr c) {
				bytes = new byte[] {(byte) c.value()};
			} else if (value instanceof Name n && n.path().size() == 2 && n.path().get(0).equals("ascii")) {
				Integer code = EastParser.ASCII.get(n.last());
				bytes = code == null ? null : new byte[] {code.byteValue()};
			}
			if (bytes != null) {
				long bits = bytes.length * 8L;
				if (pos + bits > end) {
					return null;
				}
				long saved = pos;
				try {
					for (byte b : bytes) {
						if (readUnsigned(8, false).intValue() != (b & 0xFF)) {
							return null;
						}
					}
				} finally {
					pos = saved;
				}
				return bits;
			}
			// An integer or enumeration marker: its code, in its type's size.
			View v = view(marker.type());
			Long size = v.base().size;
			if (size == null) {
				throw new EastException(marker.line(), "The marker's type '" + marker.type().toUpperCase(Locale.ROOT)
						+ "' needs a size");
			}
			BigInteger code;
			if (v.base() instanceof EnumTy e && value instanceof Name n) {
				code = e.code(n.joined());
			} else {
				BigDecimal d = EastParser.staticValue(logical, value);
				code = d == null ? null : d.toBigInteger();
			}
			if (code == null) {
				throw new EastException(marker.line(), "The marker's value must be a value of its type");
			}
			if (pos + size > end) {
				return null;
			}
			long saved = pos;
			BigInteger raw = readUnsigned(size, physical.lowOrderFirst);
			pos = saved;
			BigInteger expected = code.signum() < 0 ? code.add(BigInteger.ONE.shiftLeft(size.intValue())) : code;
			return raw.equals(expected) ? size : null;
		}

		private String markerText(Const marker) {
			Node v = marker.value();
			if (v instanceof Str s) {
				return s.value();
			}
			if (v instanceof Name n) {
				return n.raw();
			}
			if (v instanceof Chr c) {
				return String.valueOf(c.value());
			}
			BigDecimal d = EastParser.staticValue(logical, v);
			return d == null ? "?" : d.toPlainString();
		}

		// -------------------------------------------------- arrays

		private StructureNode array(String name, ArrTy a, List<Index> indices, String path, Frame frame) {
			List<Long> counts = new ArrayList<>();
			for (Index index : indices) {
				counts.add(count(index, frame));
			}
			// Outermost first: the index that varies slowest.
			List<Long> outerFirst = new ArrayList<>(counts);
			if (!physical.lastIndexFirst) {
				java.util.Collections.reverse(outerFirst);
			}
			boolean characters = view(a.element).base().name.equals("character");
			return dimension(name, a, outerFirst, 0, characters, path, frame);
		}

		private StructureNode dimension(String name, ArrTy a, List<Long> outerFirst, int level, boolean characters,
				String path, Frame frame) {
			long start = pos;
			long n = outerFirst.get(level);
			boolean innermost = level == outerFirst.size() - 1;
			if (innermost && characters) {
				byte[] text = new byte[(int) n];
				for (int i = 0; i < n; i++) {
					text[i] = (byte) readUnsigned(8, false).intValue();
				}
				String value = new String(text, StandardCharsets.ISO_8859_1);
				values.put(path, value);
				if (frame != null && level == 0) {
					frame.locals.put(name, value);
				}
				return DefaultStructureNode.builder(name, StructureNodeKind.LEAF).value(value).typeName(a.name)
						.sourceRange(new ByteRange(start, pos - start)).build();
			}
			DefaultStructureNode.Builder b = DefaultStructureNode.builder(name, StructureNodeKind.ARRAY)
					.typeName(a.name);
			for (long i = 0; i < n; i++) {
				b.addChild(innermost ? object(name, a.element, null, path, null, null)
						: dimension(name, a, outerFirst, level + 1, characters, path, frame));
			}
			return b.sourceRange(new ByteRange(start, pos - start)).build();
		}

		/** The number of values an index range covers. */
		private long count(Index index, Frame frame) {
			if (index.lo() == null) {
				Ty t = view(index.typeName()).base();
				if (t instanceof EnumTy e) {
					return e.literals.size();
				}
				if (t instanceof IntTy i) {
					return Math.max(0, number(eval(i.hi, frame)).longValue() - number(eval(i.lo, frame)).longValue() + 1);
				}
				throw new EastException(0, "The index type '" + index.typeName().toUpperCase(Locale.ROOT)
						+ "' must be an enumeration or an integer type");
			}
			Integer loOrdinal = enumOrdinal(index.lo());
			Integer hiOrdinal = enumOrdinal(index.hi());
			if (loOrdinal != null && hiOrdinal != null) {
				return Math.max(0, hiOrdinal - loOrdinal + 1);
			}
			long lo = number(eval(index.lo(), frame)).longValue();
			long hi = number(eval(index.hi(), frame)).longValue();
			return Math.max(0, hi - lo + 1);
		}

		private Integer enumOrdinal(Node n) {
			if (n instanceof Name name && name.path().size() == 1) {
				for (Ty t : logical.types.values()) {
					if (t instanceof EnumTy e && e.literals.contains(name.last())) {
						return e.literals.indexOf(name.last());
					}
				}
			}
			return null;
		}

		// -------------------------------------------------- records

		private StructureNode record(String name, RecTy r, String path) {
			long start = pos;
			Frame frame = new Frame(r, path);
			List<StructureNode> children = new ArrayList<>();
			long[] cursor = {start};
			long[] furthest = {start};
			for (Discr d : r.discriminants) {
				if (d.virtual()) {
					Node actual = logical.actuals.get(path + "." + d.name());
					if (actual == null) {
						throw new StructureInterpretationException("No actual value is given for the virtual "
								+ "discriminant " + (path + "." + d.name()).toUpperCase(Locale.ROOT));
					}
					frame.virtuals.put(d.name(), actual);
				} else {
					children.add(component(d.name(), d.type(), null, r, start, cursor, furthest, frame));
				}
			}
			components(r.comps, r, start, cursor, furthest, frame, children);
			Variant v = r.variant;
			while (v != null) {
				When w = choose(v, r, frame);
				if (w == null) {
					break;
				}
				components(w.comps(), r, start, cursor, furthest, frame, children);
				v = w.nested();
			}
			long recordEnd = r.size != null ? start + r.size : furthest[0];
			if (recordEnd < furthest[0]) {
				throw new StructureInterpretationException("The components of '" + path.toUpperCase(Locale.ROOT)
						+ "' take more than its size of " + r.size + " bits");
			}
			pos = recordEnd;
			DefaultStructureNode.Builder b = DefaultStructureNode.builder(name, StructureNodeKind.COMPOSITE)
					.typeName(r.name).sourceRange(new ByteRange(start, recordEnd - start));
			children.forEach(b::addChild);
			return b.build();
		}

		/** Reads components in order: each at its position in the representation clause, or after the one before. */
		private void components(List<Comp> comps, RecTy r, long start, long[] cursor, long[] furthest, Frame frame,
				List<StructureNode> children) {
			for (int i = 0; i < comps.size(); i++) {
				Comp c = comps.get(i);
				if (c.constant()) {
					continue;
				}
				Comp next = i + 1 < comps.size() ? comps.get(i + 1) : null;
				if (next != null && next.constant()) {
					long[] p = r.layout.get(c.name());
					pos = p != null ? start + p[0] : cursor[0];
					String path = frame.path + "." + c.name();
					children.add(repeated(c.name(), c.type(), c.constraint(), path, frame,
							new Const(next.type(), next.value(), next.line()), false));
					cursor[0] = pos;
					furthest[0] = Math.max(furthest[0], pos);
				} else {
					children.add(component(c.name(), c.type(), c.constraint(), r, start, cursor, furthest, frame));
				}
			}
		}

		private StructureNode component(String name, String type, List<Index> constraint, RecTy r, long start,
				long[] cursor, long[] furthest, Frame frame) {
			long[] p = r.layout.get(name);
			Long width = null;
			if (p != null) {
				pos = start + p[0];
				width = p[1] - p[0] + 1;
			} else {
				pos = cursor[0];
			}
			StructureNode node = object(name, type, constraint, frame.path + "." + name, frame,
					view(type).base() instanceof RecTy || view(type).base() instanceof ArrTy ? null : width);
			if (p != null) {
				pos = Math.max(pos, start + p[1] + 1);
			}
			cursor[0] = pos;
			furthest[0] = Math.max(furthest[0], pos);
			return node;
		}

		/** The alternative of a variant part the discriminant's value picks; null if none does. */
		private When choose(Variant v, RecTy r, Frame frame) {
			Object value = lookup(frame, v.discriminant(), 0);
			BigDecimal key = value instanceof Boolean b ? (b ? BigDecimal.ONE : BigDecimal.ZERO) : number(value);
			Discr d = r.discriminants.stream().filter(x -> x.name().equals(v.discriminant())).findFirst()
					.orElseThrow(() -> new EastException(v.line(), "'" + v.discriminant().toUpperCase(Locale.ROOT)
							+ "' isn't a discriminant of '" + r.name.toUpperCase(Locale.ROOT) + "'"));
			for (When w : v.whens()) {
				for (Choice c : w.choices()) {
					if (c.others()) {
						return w;
					}
					BigDecimal lo = choiceValue(c.value(), d);
					if (c.hi() == null ? key.compareTo(lo) == 0
							: key.compareTo(lo) >= 0 && key.compareTo(choiceValue(c.hi(), d)) <= 0) {
						return w;
					}
				}
			}
			return null;
		}

		private BigDecimal choiceValue(Node n, Discr d) {
			Ty t = view(d.type()).base();
			if (n instanceof Name name && t instanceof EnumTy e) {
				BigInteger code = e.code(name.joined());
				if (code == null) {
					throw new EastException(name.line(), "'" + name.raw() + "' isn't a value of "
							+ d.type().toUpperCase(Locale.ROOT));
				}
				return new BigDecimal(code);
			}
			return number(eval(n, null));
		}

		// -------------------------------------------------- scalars

		/** A scalar value: what the leaf shows, what expressions use, and the leaf's attributes. */
		private record Leaf(Object value, Object envValue, Map<String, Object> attributes) {
		}

		private Leaf scalar(String typeName, View v, String path, Long width) {
			Ty t = v.base();
			EastPhysical.Rep rep = physical.repFor(logical, typeName);
			Long size = width != null ? width : t.size;
			if (size == null) {
				throw new EastException(t.line, "'" + t.name.toUpperCase(Locale.ROOT) + "' needs a size: for "
						+ t.name.toUpperCase(Locale.ROOT) + "'size use <bits>;");
			}
			need(size, path);
			if (t instanceof EnumTy e) {
				if (e.name.equals("character")) {
					String c = String.valueOf((char) readUnsigned(8, false).intValue());
					return new Leaf(c, c, Map.of());
				}
				if (rep != null && rep.kind().equals("ascii_enumeration_physical_description")) {
					String text = text(size / 8);
					List<String> strings = rep.strings();
					int width2 = (int) (size / 8);
					for (int i = 0; i < strings.size() && i < e.literals.size(); i++) {
						if (strings.get(i) != null && pad(strings.get(i), width2).equals(text)) {
							return new Leaf(text, new BigDecimal(e.code(e.literals.get(i))),
									Map.of("meaning", e.raw.get(i)));
						}
					}
					return new Leaf(text, text, Map.of());
				}
				boolean signed = e.codes != null && e.codes.values().stream().anyMatch(c -> c.signum() < 0);
				BigInteger code = rep != null ? integerRep(rep, size, path) : read(size, signed);
				Map<String, Object> attributes = new LinkedHashMap<>();
				for (int i = 0; i < e.literals.size(); i++) {
					if (code.equals(e.code(e.literals.get(i)))) {
						attributes.put("meaning", e.raw.get(i));
					}
				}
				return new Leaf(integerValue(code), new BigDecimal(code), attributes);
			}
			if (t instanceof IntTy i) {
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(size, path, false);
				}
				BigInteger value;
				if (rep != null) {
					value = integerRep(rep, size, path);
				} else {
					BigDecimal lo = EastParser.staticValue(logical, v.lo() != null ? v.lo() : i.lo);
					value = read(size, lo != null && lo.signum() < 0);
				}
				return new Leaf(integerValue(value), new BigDecimal(value), Map.of());
			}
			if (t instanceof RealTy) {
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(size, path, true);
				}
				if (rep != null) {
					double d = realRep(rep, size, path);
					return new Leaf(d, BigDecimal.valueOf(Double.isFinite(d) ? d : 0), Map.of());
				}
				if (size == 32) {
					float f = Float.intBitsToFloat(read(32, false).intValue());
					return new Leaf(f, Float.isFinite(f) ? new BigDecimal(Float.toString(f)) : BigDecimal.ZERO, Map.of());
				}
				if (size == 64) {
					double d = Double.longBitsToDouble(read(64, false).longValue());
					return new Leaf(d, Double.isFinite(d) ? BigDecimal.valueOf(d) : BigDecimal.ZERO, Map.of());
				}
				throw new EastException(t.line, "'" + t.name.toUpperCase(Locale.ROOT) + "' is a " + size
						+ "-bit real with no representation in the physical package");
			}
			throw new StructureInterpretationException("'" + typeName.toUpperCase(Locale.ROOT)
					+ "' can't be used for data");
		}

		private Leaf asciiNumber(long size, String path, boolean real) {
			String text = text(size / 8);
			String number = text.strip().replace('d', 'e').replace('D', 'E');
			if (number.startsWith("+")) {
				number = number.substring(1);
			}
			try {
				BigDecimal d = new BigDecimal(number);
				Object value = real ? (Object) d.doubleValue() : integerValue(d.toBigIntegerExact());
				return new Leaf(value, d, Map.of("text", text));
			} catch (NumberFormatException | ArithmeticException e) {
				throw new StructureInterpretationException("'" + path.toUpperCase(Locale.ROOT) + "' should be an "
						+ (real ? "ASCII real" : "ASCII integer") + " but is \"" + text + "\"");
			}
		}

		private String text(long chars) {
			byte[] bytes = new byte[(int) chars];
			for (int i = 0; i < chars; i++) {
				bytes[i] = (byte) readUnsigned(8, false).intValue();
			}
			return new String(bytes, StandardCharsets.ISO_8859_1);
		}

		/** An integer stored contiguously, in the description's bit order; two's complement if signed. */
		private BigInteger read(long bits, boolean signed) {
			BigInteger raw = readUnsigned(bits, physical.lowOrderFirst);
			return signed && raw.testBit((int) bits - 1) ? raw.subtract(BigInteger.ONE.shiftLeft((int) bits)) : raw;
		}

		/** An integer whose INTEGER_PHYSICAL_DESCRIPTION gives its subfields and sign convention. */
		private BigInteger integerRep(EastPhysical.Rep rep, long size, String path) {
			if (!rep.kind().equals("integer_physical_description")) {
				throw new EastException(rep.line(), "The representation of '" + path.toUpperCase(Locale.ROOT)
						+ "' must be an INTEGER_PHYSICAL_DESCRIPTION");
			}
			boolean[] bits = medium(size);
			List<long[]> location = rep.location(physical.physical, "location", 2);
			if (location.isEmpty()) {
				location = List.of(new long[] {0, size - 1});
			}
			int[] width = {0};
			BigInteger raw = assemble(bits, location, width, rep, path);
			return signed(raw, width[0], rep.name("complement", 1), rep.line());
		}

		private BigInteger signed(BigInteger raw, int width, String complement, int line) {
			boolean negative = width > 0 && raw.testBit(width - 1);
			BigInteger all = BigInteger.ONE.shiftLeft(width);
			return switch (complement) {
				case "unsigned", "" -> raw;
				case "twos_complement" -> negative ? raw.subtract(all) : raw;
				case "ones_complement" -> negative ? raw.subtract(all).add(BigInteger.ONE) : raw;
				case "sign_and_magnitude" -> negative ? raw.clearBit(width - 1).negate() : raw;
				default -> throw new EastException(line, "Unknown sign convention " + complement.toUpperCase(Locale.ROOT));
			};
		}

		/**
		 * A real in a convention of CCSDS 646.0-G-1, from the sign bit and the
		 * subfields of its exponent and mantissa its REAL_PHYSICAL_DESCRIPTION
		 * gives.
		 */
		private double realRep(EastPhysical.Rep rep, long size, String path) {
			if (!rep.kind().equals("real_physical_description")) {
				throw new EastException(rep.line(), "The representation of '" + path.toUpperCase(Locale.ROOT)
						+ "' must be a REAL_PHYSICAL_DESCRIPTION");
			}
			String convention = rep.name("convention_used", 2);
			boolean[] bits = medium(size);
			long signBit = rep.number(physical.physical, "sign_bit_number", 3);
			long bias = rep.number(physical.physical, "bias", 6);
			if (convention.equals("fcstc004") && signBit < size && bits[(int) signBit]) {
				// CDC NOS-BE: a negative number is the ones' complement of the whole word.
				for (int i = 0; i < bits.length; i++) {
					bits[i] = !bits[i];
				}
				bits[(int) signBit] = true;
			}
			boolean negative = signBit < size && bits[(int) signBit];
			int[] ew = {0};
			int[] mw = {0};
			BigInteger e = assemble(bits, rep.location(physical.physical, "location_of_exponent", 7), ew, rep, path);
			BigInteger m = assemble(bits, rep.location(physical.physical, "location_of_mantissa", 8), mw, rep, path);
			double sign = negative ? -1 : 1;
			double fraction = m.doubleValue() / Math.pow(2, mw[0]);
			long exponent = e.longValue();
			switch (convention) {
				case "fcstc000" -> {
					long max = (1L << ew[0]) - 1;
					if (exponent == max) {
						return m.signum() == 0 ? sign * Double.POSITIVE_INFINITY : Double.NaN;
					}
					if (exponent == 0) {
						return sign * Math.scalb(fraction, (int) (1 - bias));
					}
					return sign * Math.scalb(1 + fraction, (int) (exponent - bias));
				}
				case "fcstc001" -> {
					if (exponent == 0) {
						return negative ? Double.NaN : 0.0;
					}
					// The redundant most significant bit (1/2) isn't stored: the first stored bit is 2^-2.
					return sign * Math.scalb(0.5 + fraction / 2, (int) (exponent - bias));
				}
				case "fcstc002" -> {
					// MIL-STD-1750A: two's complement mantissa (its first bit the sign) and exponent.
					double mantissa = signed(m, mw[0], "twos_complement", rep.line()).doubleValue()
							/ Math.pow(2, mw[0] - 1);
					long exp = signed(e, ew[0], "twos_complement", rep.line()).longValue();
					return Math.scalb(mantissa, (int) exp);
				}
				case "fcstc003" -> {
					// CDC NOS-VE: bits 1 to 3 say whether it's a number, infinite or indefinite.
					int c = (bits[1] ? 4 : 0) + (bits[2] ? 2 : 0) + (bits[3] ? 1 : 0);
					if (c == 7) {
						return Double.NaN;
					}
					if (c >= 5) {
						return sign * Double.POSITIVE_INFINITY;
					}
					if (c >= 3) {
						return sign * Math.scalb(fraction, (int) (exponent - bias));
					}
					return 0.0;
				}
				case "fcstc004" -> {
					// CDC NOS-BE: an integer mantissa, and a biased ones'-complement exponent.
					long exp = exponent >= 1024 ? exponent - 1024 : exponent - 1023;
					return sign * Math.scalb(m.doubleValue(), (int) exp);
				}
				case "fcstc005" -> {
					if (m.signum() == 0) {
						return negative ? -0.0 : 0.0;
					}
					long base = rep.number(physical.physical, "exponent_base", 5);
					return sign * fraction * Math.pow(base, exponent - bias);
				}
				default -> throw new EastException(rep.line(), "'" + path.toUpperCase(Locale.ROOT)
						+ "' is in the convention " + convention.toUpperCase(Locale.ROOT)
						+ ", which isn't one of CCSDS 646.0-G-1's (FCSTC000 to FCSTC005)");
			}
		}

		/** The bits of the subfields, most significant first, as one number; {@code width[0]} is how many. */
		private BigInteger assemble(boolean[] bits, List<long[]> location, int[] width, EastPhysical.Rep rep,
				String path) {
			BigInteger value = BigInteger.ZERO;
			for (long[] sub : location) {
				if (sub[0] < 0 || sub[1] >= bits.length || sub[1] < sub[0]) {
					throw new EastException(rep.line(), "The subfield (" + sub[0] + "," + sub[1] + ") is outside the "
							+ bits.length + " bits of '" + path.toUpperCase(Locale.ROOT) + "'");
				}
				for (long b = sub[0]; b <= sub[1]; b++) {
					value = value.shiftLeft(1);
					if (bits[(int) b]) {
						value = value.setBit(0);
					}
					width[0]++;
				}
			}
			return value;
		}

		/** The next {@code size} bits as they are on the medium: bit 0 the first, each octet's most significant first. */
		private boolean[] medium(long size) {
			boolean[] bits = new boolean[(int) size];
			for (int i = 0; i < size; i++) {
				bits[i] = bitAt(pos + i, false);
			}
			pos += size;
			return bits;
		}

		// -------------------------------------------------- bits

		private void need(long bits, String path) {
			if (pos + bits > end) {
				throw new StructureInterpretationException("The data ends at byte " + data.length + ", in the middle of '"
						+ path.toUpperCase(Locale.ROOT) + "' (which needs " + bits + " bits from bit " + pos + ")");
			}
		}

		private boolean bitAt(long bit, boolean lsbFirst) {
			int octet = data[(int) (bit >>> 3)] & 0xFF;
			int within = (int) (bit & 7);
			return ((octet >> (lsbFirst ? within : 7 - within)) & 1) == 1;
		}

		/**
		 * The next {@code bits} bits as an unsigned number: most significant
		 * bit first (HIGH_ORDER_FIRST), or least significant first, each
		 * octet's from its least significant bit (LOW_ORDER_FIRST), which for
		 * whole octets is little-endian.
		 */
		private BigInteger readUnsigned(long bits, boolean lsbFirst) {
			if (pos + bits > end) {
				throw new StructureInterpretationException("The data ends at byte " + data.length + ", " + bits
						+ " bits short");
			}
			BigInteger value = BigInteger.ZERO;
			for (long i = 0; i < bits; i++) {
				boolean bit = bitAt(pos + i, lsbFirst);
				if (lsbFirst) {
					if (bit) {
						value = value.setBit((int) i);
					}
				} else {
					value = value.shiftLeft(1);
					if (bit) {
						value = value.setBit(0);
					}
				}
			}
			pos += bits;
			return value;
		}

		// -------------------------------------------------- expressions

		/** The value of an expression: a BigDecimal, Boolean or String. */
		private Object eval(Node n, Frame frame) {
			if (n instanceof Num num) {
				return num.value();
			}
			if (n instanceof Str s) {
				return s.value();
			}
			if (n instanceof Chr c) {
				return BigDecimal.valueOf(c.value());
			}
			if (n instanceof Name name) {
				return name(name, frame);
			}
			if (n instanceof Un u) {
				Object v = eval(u.operand(), frame);
				return switch (u.op()) {
					case "-" -> number(v).negate();
					case "+" -> number(v);
					case "abs" -> number(v).abs();
					case "not" -> !bool(v);
					default -> throw new EastException(u.line(), "Unknown operator '" + u.op() + "'");
				};
			}
			if (n instanceof Bin b) {
				return binary(b, frame);
			}
			if (n instanceof Call call) {
				return call(call, frame);
			}
			if (n instanceof Agg) {
				throw new EastException(0, "An aggregate can't be used as a value here");
			}
			throw new EastException(0, "This value can't be used in an expression");
		}

		private Object name(Name name, Frame frame) {
			BigDecimal constant = EastParser.staticValue(logical, name);
			if (constant != null) {
				return constant;
			}
			if (name.path().size() == 1) {
				String s = name.last();
				if (frame != null) {
					Object local = lookupOrNull(frame, s);
					if (local != null) {
						return local;
					}
				}
				if (values.containsKey(s)) {
					return values.get(s);
				}
				for (Ty t : logical.types.values()) {
					if (t instanceof EnumTy e && e.literals.contains(s)) {
						return new BigDecimal(e.code(s));
					}
				}
				throw new StructureInterpretationException("'" + name.raw() + "' has no value yet (line "
						+ name.line() + ")");
			}
			if (name.path().size() == 2 && name.path().get(0).equals("ascii")) {
				Integer code = EastParser.ASCII.get(name.last());
				if (code != null) {
					return BigDecimal.valueOf(code);
				}
			}
			Object v = values.get(name.joined());
			if (v == null) {
				throw new StructureInterpretationException("'" + name.raw() + "' hasn't been read yet (line "
						+ name.line() + ")");
			}
			return v;
		}

		private Object lookup(Frame frame, String name, int line) {
			Object v = lookupOrNull(frame, name);
			if (v == null) {
				throw new StructureInterpretationException("'" + name.toUpperCase(Locale.ROOT) + "' has no value in '"
						+ frame.path.toUpperCase(Locale.ROOT) + "'");
			}
			return v;
		}

		private Object lookupOrNull(Frame frame, String name) {
			if (frame.locals.containsKey(name)) {
				return frame.locals.get(name);
			}
			Node actual = frame.virtuals.get(name);
			if (actual != null) {
				// Computed when first needed, from what has been read by then.
				Object v = eval(actual, null);
				frame.locals.put(name, v);
				return v;
			}
			return null;
		}

		private Object binary(Bin b, Frame frame) {
			Object l = eval(b.left(), frame);
			if (b.op().equals("and")) {
				return bool(l) && bool(eval(b.right(), frame));
			}
			if (b.op().equals("or")) {
				return bool(l) || bool(eval(b.right(), frame));
			}
			Object r = eval(b.right(), frame);
			if (b.op().equals("&")) {
				return String.valueOf(l) + r;
			}
			if (b.op().equals("=") || b.op().equals("/=")) {
				boolean equal = l instanceof BigDecimal x && r instanceof BigDecimal y ? x.compareTo(y) == 0
						: String.valueOf(l).equals(String.valueOf(r));
				return b.op().equals("=") == equal;
			}
			BigDecimal x = number(l);
			BigDecimal y = number(r);
			return switch (b.op()) {
				case "+" -> x.add(y);
				case "-" -> x.subtract(y);
				case "*" -> x.multiply(y);
				case "/" -> {
					if (y.signum() == 0) {
						throw new StructureInterpretationException("Division by zero (line " + b.line() + ")");
					}
					yield EastParser.isWhole(x) && EastParser.isWhole(y)
							? new BigDecimal(x.toBigInteger().divide(y.toBigInteger()))
							: x.divide(y, MathContext.DECIMAL64);
				}
				case "mod" -> {
					BigInteger m = x.toBigInteger().mod(y.toBigInteger().abs());
					yield new BigDecimal(y.signum() < 0 && m.signum() != 0 ? m.add(y.toBigInteger()) : m);
				}
				case "rem" -> new BigDecimal(x.toBigInteger().remainder(y.toBigInteger()));
				case "**" -> EastParser.isWhole(y) && y.signum() >= 0 && y.intValue() < 10_000
						? x.pow(y.intValue())
						: BigDecimal.valueOf(Math.pow(x.doubleValue(), y.doubleValue()));
				case "<" -> x.compareTo(y) < 0;
				case "<=" -> x.compareTo(y) <= 0;
				case ">" -> x.compareTo(y) > 0;
				case ">=" -> x.compareTo(y) >= 0;
				default -> throw new EastException(b.line(), "Unknown operator '" + b.op() + "'");
			};
		}

		private Object call(Call call, Frame frame) {
			if (call.args().size() != 1) {
				throw new EastException(call.line(), "'" + call.function() + "' takes one value");
			}
			BigDecimal x = number(eval(call.args().get(0), frame));
			double d = x.doubleValue();
			return switch (call.function()) {
				case "is_odd" -> x.toBigInteger().testBit(0);
				case "is_even" -> !x.toBigInteger().testBit(0);
				case "!" -> {
					BigInteger f = BigInteger.ONE;
					for (int i = 2; i <= x.intValue(); i++) {
						f = f.multiply(BigInteger.valueOf(i));
					}
					yield new BigDecimal(f);
				}
				case "cos" -> real(Math.cos(d));
				case "sin" -> real(Math.sin(d));
				case "tan" -> real(Math.tan(d));
				case "acos" -> real(Math.acos(d));
				case "asin" -> real(Math.asin(d));
				case "atan" -> real(Math.atan(d));
				case "log" -> real(Math.log10(d));
				case "ln" -> real(Math.log(d));
				case "cosh" -> real(Math.cosh(d));
				case "sinh" -> real(Math.sinh(d));
				case "tanh" -> real(Math.tanh(d));
				case "acosh" -> real(Math.log(d + Math.sqrt(d * d - 1)));
				case "asinh" -> real(Math.log(d + Math.sqrt(d * d + 1)));
				case "atanh" -> real(0.5 * Math.log((1 + d) / (1 - d)));
				default -> throw new EastException(call.line(), "'" + call.function() + "' isn't an EAST operator");
			};
		}

		private BigDecimal real(double d) {
			if (!Double.isFinite(d)) {
				throw new StructureInterpretationException("A computed value isn't a finite number");
			}
			return BigDecimal.valueOf(d).setScale(12, RoundingMode.HALF_EVEN).stripTrailingZeros();
		}

		private BigDecimal number(Object v) {
			if (v instanceof BigDecimal d) {
				return d;
			}
			if (v instanceof Boolean b) {
				return b ? BigDecimal.ONE : BigDecimal.ZERO;
			}
			throw new StructureInterpretationException("'" + v + "' isn't a number");
		}

		private boolean bool(Object v) {
			if (v instanceof Boolean b) {
				return b;
			}
			if (v instanceof BigDecimal d) {
				return d.signum() != 0;
			}
			throw new StructureInterpretationException("'" + v + "' isn't true or false");
		}
	}

	private static Object integerValue(BigInteger v) {
		return v.bitLength() < 64 ? (Object) v.longValue() : v;
	}

	private static String pad(String s, int width) {
		StringBuilder sb = new StringBuilder(s);
		while (sb.length() < width) {
			sb.append(' ');
		}
		return sb.toString();
	}
}
