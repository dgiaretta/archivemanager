package info.oais.infomodel.structure.east;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a Data Description Record written in EAST (CCSDS 644.0-B-3, "The
 * Data Description Language EAST Specification") as a
 * {@link FormatDescription}, so the RepInfo Tools can generate Kaitai Struct,
 * DFDL and DRB descriptions from it and test them against sample data.
 *
 * <p>An EAST description has a logical package (types, representation
 * clauses, then the variables in the order the data holds them) and a
 * physical package (byte order, array storage, and how numbers are
 * represented). The description model has no named types, so each variable
 * is expanded into elements: records into records, arrays into repeated
 * elements, enumerations into integer fields whose {@link Semantics#codes()}
 * name each literal, and integer and real ranges into
 * {@link Semantics#validMin()} and {@link Semantics#validMax()}. Record
 * representation clauses fix the order of components and the unused space
 * between them, which becomes {@code spare_n} fields. A variant part becomes
 * a {@link ChoiceDescription} when each alternative is chosen by a single
 * value, and otherwise one optional record per alternative, present when its
 * condition holds (which covers {@code |}, ranges, {@code others},
 * {@code null} alternatives and true/false discriminants). Virtual
 * discriminants are replaced by the expressions their actual values give,
 * in which an EAST path into a record read earlier ({@code LAST_DATE.DAY})
 * becomes a dotted reference ({@code last_date.day}).</p>
 *
 * <p>A description describes one set of data; unless the last variable is
 * repeated until an EOF marker, the sets repeat to the end of the data, in
 * a record called {@code set}.</p>
 *
 * <p>What the description model can't express is refused with an
 * {@link EastException} giving the line and why: markers other than EOF,
 * the {@code **} operator and functions on data values,
 * signed or least-significant-bit-first bit fields, integers in pieces or
 * not in two's complement, and reals in conventions other than IEEE 754.
 * Numbers in an ASCII representation are read as text, saying so in their
 * definition.</p>
 */
public final class EastReader {

	/**
	 * @param text an EAST Data Description Record: the logical package, then the physical package
	 * @return the description it gives
	 * @throws EastException if it can't be read, or uses what the description model can't express
	 */
	public static FormatDescription read(String text) {
		EastParser.Description d = EastParser.parse(text);
		return new Builder(d.logical(), d.physical()).build();
	}

	// ------------------------------------------------------------------ building the description

	/** How the physical package says a scalar type is represented. */
	private record Rep(String kind, Agg value, int line) {
	}

	/** What one element became, and how many bits it takes when that's known. */
	private record Built(ElementDescription element, Long bits, boolean byteMultiple) {
	}

	/**
	 * Where an element is being built: its EAST path, those of the record
	 * instances enclosing it, and the values the enclosing record's
	 * discriminants stand for.
	 */
	private record Ctx(String path, List<String> ancestors, Map<String, Expression> bindings) {
		Ctx child(String name) {
			return new Ctx(path == null ? name : path + "." + name, ancestors, bindings);
		}
	}

	private static final class Builder {
		private final Pkg logical;
		private final Pkg physical;
		private ByteOrder order = ByteOrder.BIG_ENDIAN;
		private boolean lastIndexFirst;
		private final Map<String, Rep> reps = new HashMap<>();
		private final List<String> asText = new ArrayList<>();

		Builder(Pkg logical, Pkg physical) {
			this.logical = logical;
			this.physical = physical;
			EastParser.predefine(logical);
		}

		FormatDescription build() {
			readPhysical();
			for (Map.Entry<String, Const> marker : logical.markers.entrySet()) {
				throw new EastException(marker.getValue().line(), "A marker ends the repetition of '"
						+ marker.getKey().toUpperCase(Locale.ROOT) + "': it repeats until the marker's value is "
						+ "found. Markers other than EOF can't be expressed in the element tree (the EAST "
						+ "interpreter reads them)");
			}
			if (logical.vars.isEmpty()) {
				throw new EastException(0, "The logical package '" + logical.raw + "' declares no variables, "
						+ "so it doesn't describe any data");
			}
			List<ElementDescription> children = new ArrayList<>();
			Integer phase = 0;
			for (int i = 0; i < logical.vars.size(); i++) {
				Var v = logical.vars.get(i);
				Ctx ctx = new Ctx(v.name(), List.of(), Map.of());
				Built b = object(v.name(), v.type(), v.constraint(), ctx, phase, v.line());
				ElementDescription e = b.element();
				if (logical.untilEof && i == logical.vars.size() - 1) {
					e = repeat(e, new Occurrence.UntilEnd());
				}
				children.add(e);
				phase = advance(phase, b);
			}
			RecordDescription root;
			String rootName = logical.name;
			if (logical.untilEof) {
				root = new RecordDescription(ElementDescription.newId(), rootName, children, null, Occurrence.ONCE,
						Semantics.NONE);
			} else {
				RecordDescription set = new RecordDescription(ElementDescription.newId(), "set", children, null,
						new Occurrence.UntilEnd(), Semantics.of(null,
								"One set of the data the EAST description describes; the description is applied "
										+ "repeatedly to the whole of the data.", null));
				root = new RecordDescription(ElementDescription.newId(), rootName, List.of(set), null,
						Occurrence.ONCE, Semantics.NONE);
			}
			StringBuilder notes = new StringBuilder("Read from an EAST Data Description Record (CCSDS 644.0-B-3): "
					+ "logical package " + logical.raw);
			if (physical != null) {
				notes.append(", physical package ").append(physical.raw);
			}
			notes.append(logical.version != null ? "; EAST version " + logical.version + "." : ".");
			if (!asText.isEmpty()) {
				notes.append(" Numbers in an ASCII representation are read as text: ")
						.append(String.join(", ", asText)).append('.');
			}
			return new FormatDescription(logical.raw, notes.toString(), order, List.of(), root);
		}

		// -------------------------------------------------- physical package

		private void readPhysical() {
			if (physical == null) {
				return;
			}
			Const octets = physical.constants.get("octet_storage");
			if (octets != null && octets.value() instanceof Name n) {
				if (n.joined().equals("low_order_first")) {
					order = ByteOrder.LITTLE_ENDIAN;
				} else if (!n.joined().equals("high_order_first")) {
					throw new EastException(octets.line(), "OCTET_STORAGE must be HIGH_ORDER_FIRST or LOW_ORDER_FIRST");
				}
			}
			Const arrays = physical.constants.get("array_storage");
			if (arrays != null && arrays.value() instanceof Name n) {
				lastIndexFirst = n.joined().equals("last_index_first");
			}
			if (physical.types.get("relation") instanceof RecTy relation && relation.variant != null) {
				for (When w : relation.variant.whens()) {
					for (Choice c : w.choices()) {
						if (!(c.value() instanceof Name n) || !n.joined().startsWith("user_type_")) {
							continue;
						}
						String userType = n.joined().substring("user_type_".length());
						for (Comp comp : w.comps()) {
							if (comp.value() instanceof Name constName) {
								Const rep = physical.constants.get(constName.joined());
								if (rep == null || !(rep.value() instanceof Agg agg)) {
									throw new EastException(comp.line(), "'" + constName.raw()
											+ "' isn't declared in the physical package with a value");
								}
								reps.put(userType, new Rep(comp.type(), agg, rep.line()));
							}
						}
					}
				}
			}
		}

		/** The representation the physical package gives a type, or one it's a subtype of. */
		private Rep repFor(String typeName) {
			String name = typeName;
			for (int i = 0; i < 50 && name != null; i++) {
				Rep rep = reps.get(name);
				if (rep != null) {
					return rep;
				}
				name = logical.types.get(name) instanceof SubTy s ? s.base : null;
			}
			return null;
		}

		// -------------------------------------------------- types

		private Ty type(String name, int line) {
			Ty t = logical.types.get(name);
			if (t == null) {
				throw new EastException(line, "The type '" + name.toUpperCase(Locale.ROOT) + "' isn't declared");
			}
			return t;
		}

		/** A type with its subtypes' constraints applied. */
		private record View(Ty base, List<Index> constraint, Node lo, Node hi) {
		}

		private View view(String name, int line) {
			Ty t = type(name, line);
			List<Index> constraint = null;
			Node lo = null;
			Node hi = null;
			for (int i = 0; t instanceof SubTy s; i++) {
				if (i > 50) {
					throw new EastException(line, "The subtypes of '" + name + "' go round in a circle");
				}
				if (constraint == null) {
					constraint = s.constraint;
				}
				if (lo == null) {
					lo = s.lo;
					hi = s.hi;
				}
				t = type(s.base, s.line);
			}
			return new View(t, constraint, lo, hi);
		}

		private boolean isCharacter(String name, int line) {
			return view(name, line).base().name.equals("character");
		}

		private Long size(Ty t, int line) {
			if (t.size == null) {
				throw new EastException(t.line > 0 ? t.line : line, "'" + t.name.toUpperCase(Locale.ROOT)
						+ "' needs a size: for " + t.name.toUpperCase(Locale.ROOT) + "'size use <bits>;");
			}
			return t.size;
		}

		// -------------------------------------------------- elements

		private Built object(String name, String typeName, List<Index> constraint, Ctx ctx, Integer phase, int line) {
			View v = view(typeName, line);
			Ty t = v.base();
			if (t instanceof RecTy r) {
				if (constraint != null) {
					throw new EastException(line, "A record can't be given an index constraint");
				}
				return record(name, r, ctx, phase, line);
			}
			if (t instanceof ArrTy a) {
				List<Index> indices = constraint != null ? constraint : v.constraint() != null ? v.constraint()
						: a.indices;
				if (indices.stream().anyMatch(Index::unconstrained)) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' needs the number of elements "
							+ "of " + typeName.toUpperCase(Locale.ROOT) + ", e.g. (1 .. 10)");
				}
				if (indices.size() != a.indices.size()) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' gives "
							+ indices.size() + " index ranges for an array with " + a.indices.size());
				}
				return array(name, a, indices, ctx, phase, line);
			}
			if (constraint != null) {
				throw new EastException(line, "Only an array can be given an index constraint");
			}
			return scalar(name, typeName, v, phase, line);
		}

		private Built scalar(String name, String typeName, View v, Integer phase, int line) {
			Ty t = v.base();
			Rep rep = repFor(typeName);
			if (t instanceof EnumTy e) {
				if (e.name.equals("character")) {
					return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(1), Semantics.NONE),
							8L, true);
				}
				long bits = size(e, line);
				Map<String, String> codes = new LinkedHashMap<>();
				if (rep != null) {
					if (!rep.kind().equals("ascii_enumeration_physical_description")) {
						throw new EastException(rep.line(), "The representation of the enumeration '"
								+ typeName.toUpperCase(Locale.ROOT) + "' must be an ASCII_ENUMERATION_PHYSICAL_DESCRIPTION");
					}
					long chars = number(rep, "number_of_characters", 1);
					checkChars(typeName, bits, chars, rep.line());
					Node strings = rep.value().get("representation", 2);
					if (!(strings instanceof Agg list) || list.items().size() != e.literals.size()) {
						throw new EastException(rep.line(), "The ASCII representation of '"
								+ typeName.toUpperCase(Locale.ROOT) + "' needs one string for each literal");
					}
					for (int i = 0; i < e.literals.size(); i++) {
						if (!(list.items().get(i).value() instanceof Str s)) {
							throw new EastException(rep.line(), "The ASCII representation of '"
									+ typeName.toUpperCase(Locale.ROOT) + "' must be strings");
						}
						codes.put(pad(s.value(), (int) chars), e.raw.get(i));
					}
					return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(chars),
							new Semantics(null, null, null, null, null, codes, null, null, null, null, null)),
							bits, bits % 8 == 0);
				}
				boolean signed = false;
				for (int i = 0; i < e.literals.size(); i++) {
					BigInteger code = e.code(e.literals.get(i));
					signed |= code.signum() < 0;
					codes.put(code.toString(), e.raw.get(i));
				}
				return new Built(integer(name, typeName, bits, signed, phase, line,
						new Semantics(null, null, null, null, null, codes, null, null, null, null, null)), bits,
						bits % 8 == 0);
			}
			if (t instanceof IntTy i) {
				long bits = size(i, line);
				BigDecimal lo = EastParser.staticValue(logical, v.lo() != null ? v.lo() : i.lo);
				BigDecimal hi = EastParser.staticValue(logical, v.hi() != null ? v.hi() : i.hi);
				if (lo == null || hi == null) {
					throw new EastException(i.line, "The range of '" + i.name.toUpperCase(Locale.ROOT)
							+ "' must be numbers or number constants");
				}
				Semantics range = new Semantics(null, null, null, null, null, Map.of(), null, null, null, lo, hi);
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(name, typeName, "integer", bits, rep, range);
				}
				ByteOrder fieldOrder = rep != null ? integerRepOrder(typeName, rep, bits, lo.signum() < 0) : null;
				FieldDescription f = integer(name, typeName, bits, lo.signum() < 0, phase, line, range);
				return new Built(withOrder(f, fieldOrder), bits, bits % 8 == 0);
			}
			if (t instanceof RealTy r) {
				long bits = size(r, line);
				Node loNode = v.lo() != null ? v.lo() : r.lo;
				Node hiNode = v.hi() != null ? v.hi() : r.hi;
				BigDecimal lo = loNode == null ? null : EastParser.staticValue(logical, loNode);
				BigDecimal hi = hiNode == null ? null : EastParser.staticValue(logical, hiNode);
				Semantics range = new Semantics(null, null, null, null, null, Map.of(), null, null, null, lo, hi);
				if (rep != null && rep.kind().equals("ascii_numeric_physical_description")) {
					return asciiNumber(name, typeName, "real", bits, rep, range);
				}
				ByteOrder fieldOrder = null;
				if (rep != null) {
					Node convention = rep.value().get("convention_used", 2);
					if (!(convention instanceof Name c) || !c.joined().equals("fcstc000")) {
						throw new EastException(rep.line(), "The real type '" + typeName.toUpperCase(Locale.ROOT)
								+ "' isn't in IEEE 754 (FCSTC000); the element tree has only IEEE 754 reals "
								+ "(the EAST interpreter reads the other conventions)");
					}
					// IEEE 754 laid out big-endian has its sign bit first; little-endian, in its last octet.
					long sign = number(rep, "sign_bit_number", 3);
					fieldOrder = sign == 0 ? ByteOrder.BIG_ENDIAN : sign == bits - 8 ? ByteOrder.LITTLE_ENDIAN : null;
					if (fieldOrder == null) {
						throw new EastException(rep.line(), "The real type '" + typeName.toUpperCase(Locale.ROOT)
								+ "' is IEEE 754 laid out neither big- nor little-endian");
					}
				}
				if (phase != null && phase != 0) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT)
							+ "' is a real that doesn't start on a byte boundary, which isn't supported");
				}
				PrimitiveType type = bits == 32 ? PrimitiveType.FLOAT32 : bits == 64 ? PrimitiveType.FLOAT64 : null;
				if (type == null) {
					throw new EastException(r.line, "'" + r.name.toUpperCase(Locale.ROOT) + "' is a " + bits
							+ "-bit real; only 32- and 64-bit IEEE 754 reals are supported");
				}
				return new Built(withOrder(field(name, type, null, range), fieldOrder), bits, true);
			}
			throw new EastException(line, "'" + typeName.toUpperCase(Locale.ROOT) + "' can't be used for data");
		}

		private Built asciiNumber(String name, String typeName, String kind, long bits, Rep rep, Semantics range) {
			long chars = number(rep, "number_of_characters", 0);
			checkChars(typeName, bits, chars, rep.line());
			if (!asText.contains(typeName.toUpperCase(Locale.ROOT))) {
				asText.add(typeName.toUpperCase(Locale.ROOT));
			}
			Semantics s = new Semantics(null, "An EAST ASCII-encoded decimal " + kind + " in " + chars
					+ " characters.", null, null, null, Map.of(), null, null, null, range.validMin(), range.validMax());
			return new Built(field(name, PrimitiveType.STRING, new Expression.IntLiteral(chars), s), bits, true);
		}

		private static void checkChars(String typeName, long bits, long chars, int line) {
			if (chars * 8 != bits) {
				throw new EastException(line, "'" + typeName.toUpperCase(Locale.ROOT) + "' is " + bits
						+ " bits but its ASCII representation is " + chars + " characters");
			}
		}

		private static String pad(String s, int width) {
			StringBuilder sb = new StringBuilder(s);
			while (sb.length() < width) {
				sb.append(' ');
			}
			return sb.toString();
		}

		private long number(Rep rep, String component, int index) {
			Node n = rep.value().get(component, index);
			BigDecimal v = n == null ? null : EastParser.staticValue(physical, n);
			if (v == null) {
				throw new EastException(rep.line(), "The representation needs " + component.toUpperCase(Locale.ROOT));
			}
			return v.longValue();
		}

		/** A field in the byte order its representation gives, when that isn't the description's own. */
		private FieldDescription withOrder(FieldDescription f, ByteOrder fieldOrder) {
			return fieldOrder == null || fieldOrder == order || f.type().fixedWidth() <= 1 ? f
					: f.withByteOrder(fieldOrder);
		}

		/**
		 * Accepts the binary integer representations every engine reads: two's
		 * complement or unsigned, its bits in one run (big-endian) or in whole
		 * octets from the last to the first (little-endian).
		 *
		 * @return the byte order the subfields give; null if they don't say
		 */
		private ByteOrder integerRepOrder(String typeName, Rep rep, long bits, boolean negative) {
			String what = "'" + typeName.toUpperCase(Locale.ROOT) + "'";
			if (!rep.kind().equals("integer_physical_description")) {
				throw new EastException(rep.line(), "The representation of the integer " + what
						+ " must be an INTEGER_PHYSICAL_DESCRIPTION or ASCII_NUMERIC_PHYSICAL_DESCRIPTION");
			}
			Node complement = rep.value().get("complement", 1);
			String c = complement instanceof Name n ? n.joined() : "";
			if (!c.equals("twos_complement") && !c.equals("unsigned")) {
				throw new EastException(rep.line(), what + " is in " + c.toUpperCase(Locale.ROOT)
						+ "; the element tree has only two's complement and unsigned integers (the EAST "
						+ "interpreter reads the others)");
			}
			if (c.equals("unsigned") && negative) {
				throw new EastException(rep.line(), what + " has negative values, so it can't be UNSIGNED");
			}
			List<long[]> subfields = new ArrayList<>();
			if (rep.value().get("location", 2) instanceof Agg location) {
				for (Agg.Item item : location.items()) {
					BigDecimal first = item.value() instanceof Agg r
							? EastParser.staticValue(physical, r.get("beginning_at_bit_number", 0)) : null;
					BigDecimal last = item.value() instanceof Agg r
							? EastParser.staticValue(physical, r.get("ending_at_bit_number", 1)) : null;
					if (first == null || last == null) {
						throw new EastException(rep.line(), "Each subfield of " + what + " must be two bit numbers");
					}
					subfields.add(new long[] {first.longValue(), last.longValue()});
				}
			}
			if (subfields.isEmpty()) {
				return null;
			}
			if (subfields.size() == 1 && subfields.get(0)[0] == 0 && subfields.get(0)[1] == bits - 1) {
				return ByteOrder.BIG_ENDIAN;
			}
			boolean littleEndian = bits % 8 == 0 && subfields.size() == bits / 8;
			for (int i = 0; littleEndian && i < subfields.size(); i++) {
				long octet = bits / 8 - 1 - i;
				littleEndian = subfields.get(i)[0] == octet * 8 && subfields.get(i)[1] == octet * 8 + 7;
			}
			if (!littleEndian) {
				throw new EastException(rep.line(), "The bits of " + what + " are in subfields that are neither "
						+ "big- nor little-endian; the element tree can't express that (the EAST interpreter reads it)");
			}
			return ByteOrder.LITTLE_ENDIAN;
		}

		private FieldDescription integer(String name, String typeName, long bits, boolean signed, Integer phase,
				int line, Semantics semantics) {
			boolean aligned = phase == null || phase == 0;
			if (aligned && (bits == 8 || bits == 16 || bits == 32 || bits == 64)) {
				PrimitiveType type = switch ((int) bits) {
					case 8 -> signed ? PrimitiveType.INT8 : PrimitiveType.UINT8;
					case 16 -> signed ? PrimitiveType.INT16 : PrimitiveType.UINT16;
					case 32 -> signed ? PrimitiveType.INT32 : PrimitiveType.UINT32;
					default -> signed ? PrimitiveType.INT64 : PrimitiveType.UINT64;
				};
				return field(name, type, null, semantics);
			}
			if (signed) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' (" + typeName.toUpperCase(Locale.ROOT)
						+ ") is a signed " + bits + "-bit field; signed bit fields aren't supported yet");
			}
			if (bits > 64) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' is " + bits
						+ " bits; integers of more than 64 bits aren't supported");
			}
			if (order == ByteOrder.LITTLE_ENDIAN) {
				throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT) + "' is a " + bits
						+ "-bit field in LOW_ORDER_FIRST data; bit fields stored least significant bit first "
						+ "aren't supported yet");
			}
			return field(name, PrimitiveType.BITS, new Expression.IntLiteral(bits), semantics);
		}

		private static FieldDescription field(String name, PrimitiveType type, Expression length, Semantics semantics) {
			return new FieldDescription(ElementDescription.newId(), name, type, length, null, Occurrence.ONCE,
					semantics);
		}

		// -------------------------------------------------- arrays

		private Built array(String name, ArrTy a, List<Index> indices, Ctx ctx, Integer phase, int line) {
			List<Expression> counts = new ArrayList<>();
			for (Index index : indices) {
				counts.add(count(index, ctx, line));
			}
			// The index that varies fastest is innermost.
			List<Expression> fastestFirst = new ArrayList<>(counts);
			if (lastIndexFirst) {
				Collections.reverse(fastestFirst);
			}
			Long total = 1L;
			for (Expression c : counts) {
				total = c instanceof Expression.IntLiteral lit && total != null ? total * lit.value() : null;
			}
			int levels = fastestFirst.size();
			Built inner;
			int start;
			if (isCharacter(a.element, line)) {
				String innerName = levels == 1 ? name : "element";
				inner = new Built(field(innerName, PrimitiveType.STRING, fastestFirst.get(0), Semantics.NONE),
						fastestFirst.get(0) instanceof Expression.IntLiteral lit ? lit.value() * 8 : null, true);
				start = 1;
			} else {
				String innerName = levels == 1 ? name : "element";
				Long elementBits = staticSize(a.element, line);
				Integer elementPhase = elementBits != null && elementBits % 8 != 0 ? Integer.valueOf(1) : phase;
				Built element = object(innerName, a.element, null, ctx, elementPhase, line);
				if (elementBits != null && elementBits % 8 != 0 && phase != null && phase != 0) {
					throw new EastException(line, "'" + name.toUpperCase(Locale.ROOT)
							+ "' is an array of bit fields that doesn't start on a byte boundary");
				}
				Expression n = fastestFirst.get(0);
				inner = new Built(repeat(element.element(), new Occurrence.Repeated(n)),
						element.bits() != null && n instanceof Expression.IntLiteral lit ? element.bits() * lit.value()
								: null, element.byteMultiple());
				start = 1;
			}
			for (int level = start; level < levels; level++) {
				String levelName = level == levels - 1 ? name : "dim_" + (level + 1);
				Expression n = fastestFirst.get(level);
				RecordDescription wrapper = new RecordDescription(ElementDescription.newId(), levelName,
						List.of(inner.element()), null, new Occurrence.Repeated(n), Semantics.NONE);
				inner = new Built(wrapper, inner.bits() != null && n instanceof Expression.IntLiteral lit
						? inner.bits() * lit.value() : null, inner.byteMultiple());
			}
			if (a.size != null && inner.bits() != null && !a.size.equals(inner.bits())) {
				throw new EastException(a.line, "'" + a.name.toUpperCase(Locale.ROOT) + "' is given a size of "
						+ a.size + " bits, but its elements take " + inner.bits());
			}
			return inner;
		}

		/** The number of values an index range covers, as an expression. */
		private Expression count(Index index, Ctx ctx, int line) {
			if (index.lo() == null) {
				Ty t = view(index.typeName(), line).base();
				if (t instanceof EnumTy e) {
					return new Expression.IntLiteral(e.literals.size());
				}
				if (t instanceof IntTy i) {
					BigDecimal lo = EastParser.staticValue(logical, i.lo);
					BigDecimal hi = EastParser.staticValue(logical, i.hi);
					if (lo != null && hi != null) {
						return new Expression.IntLiteral(Math.max(0, hi.longValue() - lo.longValue() + 1));
					}
				}
				throw new EastException(line, "The index type '" + index.typeName().toUpperCase(Locale.ROOT)
						+ "' must be an enumeration or an integer range of numbers");
			}
			Integer loOrdinal = enumOrdinal(index.lo());
			Integer hiOrdinal = enumOrdinal(index.hi());
			if (loOrdinal != null && hiOrdinal != null) {
				return new Expression.IntLiteral(Math.max(0, hiOrdinal - loOrdinal + 1));
			}
			BigDecimal lo = EastParser.staticValue(logical, index.lo());
			BigDecimal hi = EastParser.staticValue(logical, index.hi());
			if (lo != null && hi != null) {
				return new Expression.IntLiteral(Math.max(0, hi.longValue() - lo.longValue() + 1));
			}
			Expression high = expression(index.hi(), ctx, line);
			if (lo != null && lo.longValue() == 1) {
				return high;
			}
			Expression low = lo != null ? new Expression.IntLiteral(lo.longValue()) : expression(index.lo(), ctx, line);
			return new Expression.Binary(Expression.Op.ADD, new Expression.Binary(Expression.Op.SUB, high, low),
					new Expression.IntLiteral(1));
		}

		private Integer enumOrdinal(Node n) {
			if (n instanceof Name name && name.path().size() == 1) {
				for (Ty t : logical.types.values()) {
					if (t instanceof EnumTy e && e.literals.contains(name.last())) {
						return e.literals.indexOf(name.last());
					}
				}
			}
			if (n instanceof Chr c) {
				return (int) c.value();
			}
			return null;
		}

		/** The size of a type when it doesn't depend on the data; null otherwise. */
		private Long staticSize(String typeName, int line) {
			View v = view(typeName, line);
			Ty t = v.base();
			if (t.size != null) {
				return t.size;
			}
			if (t instanceof ArrTy a) {
				List<Index> indices = v.constraint() != null ? v.constraint() : a.indices;
				Long element = staticSize(a.element, line);
				if (element == null) {
					return null;
				}
				long total = element;
				for (Index index : indices) {
					if (index.unconstrained()) {
						return null;
					}
					Expression c;
					try {
						c = count(index, new Ctx(null, List.of(), Map.of()), line);
					} catch (EastException e) {
						return null;
					}
					if (!(c instanceof Expression.IntLiteral lit)) {
						return null;
					}
					total *= lit.value();
				}
				return total;
			}
			return null;
		}

		// -------------------------------------------------- records

		/** One component to place in a record: its name, type, and position from the representation clause. */
		private record Part(String name, String type, List<Index> constraint, long[] position, int line) {
		}

		private Built record(String name, RecTy r, Ctx outer, Integer phase, int line) {
			List<String> ancestors = new ArrayList<>(outer.ancestors());
			ancestors.add(outer.path());
			Map<String, Expression> bindings = new HashMap<>();
			Ctx ctx = new Ctx(outer.path(), ancestors, bindings);
			List<Part> fixed = new ArrayList<>();
			for (Discr d : r.discriminants) {
				if (d.virtual()) {
					String path = outer.path() + "." + d.name();
					Node actual = logical.actuals.get(path);
					if (actual == null) {
						throw new EastException(d.line(), "No actual value is given for the virtual discriminant "
								+ path.toUpperCase(Locale.ROOT) + " (" + path.toUpperCase(Locale.ROOT)
								+ " : virtual <type> := <value>;)");
					}
					bindings.put(d.name(), expression(actual, ctx, logical.actualLines.get(path)));
				} else {
					bindings.put(d.name(), new Expression.FieldRef(d.name()));
					fixed.add(new Part(d.name(), d.type(), null, r.layout.get(d.name()), d.line()));
				}
			}
			for (Comp c : r.comps) {
				if (c.constant()) {
					throw new EastException(c.line(), "'" + c.name().toUpperCase(Locale.ROOT) + "' is a marker: the "
							+ "component before it repeats until this value is found. Markers aren't supported yet");
				}
				fixed.add(new Part(c.name(), c.type(), c.constraint(), r.layout.get(c.name()), c.line()));
			}
			Body body = body(fixed, r.variant, r, 0, r.size, ctx, phase);
			RecordDescription record = new RecordDescription(ElementDescription.newId(), name, body.children(), null,
					Occurrence.ONCE, Semantics.NONE);
			return new Built(record, body.bits(), body.byteMultiple());
		}

		private record Body(List<ElementDescription> children, Long bits, boolean byteMultiple) {
		}

		/**
		 * Lays out a record's components, or one alternative of its variant
		 * part: those with a position in order of position, with the unused
		 * space between them, then the rest in the order declared.
		 *
		 * @param base  where this body starts, in bits from the start of the record
		 * @param padTo how many bits the body must take, if known: unused space is added at its end
		 */
		private Body body(List<Part> parts, Variant variant, RecTy r, long base, Long padTo, Ctx ctx,
				Integer phase) {
			List<ElementDescription> out = new ArrayList<>();
			int[] spares = {0};
			long[] variantSpan = null;
			if (variant != null) {
				List<String> names = new ArrayList<>();
				variant.names(names);
				for (String n : names) {
					long[] p = r.layout.get(n);
					if (p != null) {
						variantSpan = variantSpan == null ? new long[] {p[0], p[1]}
								: new long[] {Math.min(variantSpan[0], p[0]), Math.max(variantSpan[1], p[1])};
					}
				}
			}
			List<Object> placed = new ArrayList<>();
			List<Part> rest = new ArrayList<>();
			for (Part p : parts) {
				if (p.position() != null) {
					placed.add(p);
				} else {
					rest.add(p);
				}
			}
			if (variantSpan != null) {
				placed.add(variantSpan);
			}
			placed.sort((x, y) -> Long.compare(start(x), start(y)));
			long cursor = 0;
			boolean known = true;
			boolean byteMultiple = true;
			Integer ph = phase;
			for (Object item : placed) {
				long at = start(item) - base;
				if (at < cursor) {
					throw new EastException(lineOf(item, variant), "'" + nameOf(item) + "' overlaps the component "
							+ "before it in the representation clause of '" + r.name.toUpperCase(Locale.ROOT) + "'");
				}
				if (at > cursor) {
					ph = spare(out, spares, at - cursor, ph);
					cursor = at;
				}
				long width = end(item) - start(item) + 1;
				if (item instanceof Part p) {
					Built b = object(p.name(), p.type(), p.constraint(), ctx.child(p.name()), ph, p.line());
					if (b.bits() != null && b.bits() != width) {
						throw new EastException(p.line(), "'" + p.name().toUpperCase(Locale.ROOT) + "' takes "
								+ b.bits() + " bits, but its representation clause gives it " + width);
					}
					out.add(b.element());
				} else {
					variantElements(variant, r, start(item), width, ctx, ph, out);
				}
				cursor += width;
				ph = ph == null ? null : (int) ((ph + width) % 8);
			}
			for (Part p : rest) {
				Built b = object(p.name(), p.type(), p.constraint(), ctx.child(p.name()), ph, p.line());
				out.add(b.element());
				if (b.bits() != null && known) {
					cursor += b.bits();
				} else {
					known = false;
				}
				byteMultiple &= b.byteMultiple();
				ph = advance(ph, b);
			}
			if (variant != null && variantSpan == null) {
				Built v = variantElements(variant, r, -1, null, ctx, ph, out);
				if (v.bits() != null && known) {
					cursor += v.bits();
				} else {
					known = false;
				}
				byteMultiple &= v.byteMultiple();
				ph = advance(ph, v);
			}
			if (padTo != null && known) {
				if (cursor > padTo) {
					throw new EastException(r.line, "The components of '" + r.name.toUpperCase(Locale.ROOT)
							+ "' take " + cursor + " bits, more than its size of " + padTo);
				}
				if (cursor < padTo) {
					ph = spare(out, spares, padTo - cursor, ph);
					cursor = padTo;
				}
			}
			return new Body(out, known ? Long.valueOf(cursor) : null, known ? cursor % 8 == 0 : byteMultiple);
		}

		private static long start(Object item) {
			return item instanceof Part p ? p.position()[0] : ((long[]) item)[0];
		}

		private static long end(Object item) {
			return item instanceof Part p ? p.position()[1] : ((long[]) item)[1];
		}

		private static String nameOf(Object item) {
			return item instanceof Part p ? p.name().toUpperCase(Locale.ROOT) : "the variant part";
		}

		private static int lineOf(Object item, Variant variant) {
			return item instanceof Part p ? p.line() : variant.line();
		}

		/** Adds unused space as {@code spare_n} fields: bytes when on a byte boundary, else bit fields. */
		private Integer spare(List<ElementDescription> out, int[] spares, long bits, Integer phase) {
			long left = bits;
			Integer ph = phase;
			while (left > 0) {
				String name = "spare_" + (++spares[0]);
				Semantics unused = Semantics.of(null, "Unused space.", null);
				if ((ph == null || ph == 0) && left % 8 == 0) {
					out.add(field(name, PrimitiveType.BYTES, new Expression.IntLiteral(left / 8), unused));
					left = 0;
				} else {
					long chunk = ph != null && ph != 0 ? Math.min(left, 8 - ph) : Math.min(left, left % 8 == 0 ? 8 : left % 8);
					if (order == ByteOrder.LITTLE_ENDIAN) {
						throw new EastException(0, "Unused space of " + bits + " bits in LOW_ORDER_FIRST data isn't "
								+ "supported yet");
					}
					out.add(field(name, PrimitiveType.BITS, new Expression.IntLiteral(chunk), unused));
					left -= chunk;
					ph = ph == null ? null : (int) ((ph + chunk) % 8);
				}
			}
			return ph;
		}

		/**
		 * The variant part: a choice when each alternative is chosen by one
		 * value, otherwise an optional record for each alternative.
		 *
		 * @param at    where it starts, in bits from the start of the record; -1 if it isn't positioned
		 * @param width how many bits it takes, if the representation clause says
		 */
		private Built variantElements(Variant v, RecTy r, long at, Long width, Ctx ctx, Integer phase,
				List<ElementDescription> out) {
			Expression discriminant = ctx.bindings().get(v.discriminant());
			if (discriminant == null) {
				throw new EastException(v.line(), "'" + v.discriminant().toUpperCase(Locale.ROOT)
						+ "' isn't a discriminant of '" + r.name.toUpperCase(Locale.ROOT) + "'");
			}
			Discr d = r.discriminants.stream().filter(x -> x.name().equals(v.discriminant())).findFirst().orElseThrow();
			boolean simple = !discriminant.isBoolean() && v.whens().stream().allMatch(w -> w.choices().size() == 1
					&& !w.choices().get(0).others() && w.choices().get(0).hi() == null
					&& (!w.comps().isEmpty() || w.nested() != null));
			List<Body> bodies = new ArrayList<>();
			for (When w : v.whens()) {
				List<Part> parts = new ArrayList<>();
				for (Comp c : w.comps()) {
					if (c.constant()) {
						throw new EastException(c.line(), "Markers aren't supported yet");
					}
					parts.add(new Part(c.name(), c.type(), c.constraint(), r.layout.get(c.name()), c.line()));
				}
				bodies.add(body(parts, w.nested(), r, Math.max(at, 0), width, ctx, phase));
			}
			Long bits = width;
			if (bits == null && !bodies.isEmpty() && bodies.stream().allMatch(b -> b.bits() != null)
					&& bodies.stream().map(Body::bits).distinct().count() == 1) {
				bits = bodies.get(0).bits();
			}
			boolean byteMultiple = bodies.stream().allMatch(Body::byteMultiple);
			if (simple) {
				List<ChoiceDescription.Branch> branches = new ArrayList<>();
				for (int i = 0; i < v.whens().size(); i++) {
					When w = v.whens().get(i);
					BigInteger key = choiceValue(w.choices().get(0).value(), d, w.line());
					branches.add(new ChoiceDescription.Branch(key.toString(), new RecordDescription(
							ElementDescription.newId(), alternativeName(w, d), bodies.get(i).children(), null,
							Occurrence.ONCE, Semantics.NONE)));
				}
				out.add(new ChoiceDescription(ElementDescription.newId(),
						"case_" + v.discriminant().replaceFirst("^virtual_", ""), discriminant,
						branches, Occurrence.ONCE, Semantics.NONE));
				return new Built(out.get(out.size() - 1), bits, byteMultiple);
			}
			Expression others = null;
			for (When w : v.whens()) {
				for (Choice c : w.choices()) {
					if (!c.others()) {
						Expression test = test(discriminant, c, d, w.line());
						others = others == null ? test : new Expression.Binary(Expression.Op.OR, others, test);
					}
				}
			}
			ElementDescription last = null;
			for (int i = 0; i < v.whens().size(); i++) {
				When w = v.whens().get(i);
				if (bodies.get(i).children().isEmpty()) {
					continue;
				}
				Expression condition = null;
				for (Choice c : w.choices()) {
					Expression test = c.others()
							? others == null ? null : new Expression.Unary(Expression.Op.NOT, others)
							: test(discriminant, c, d, w.line());
					if (test == null) {
						condition = null;
						break;
					}
					condition = condition == null ? test : new Expression.Binary(Expression.Op.OR, condition, test);
				}
				Occurrence occurrence = condition == null ? Occurrence.ONCE : new Occurrence.Optional(condition);
				last = new RecordDescription(ElementDescription.newId(), alternativeName(w, d),
						bodies.get(i).children(), null, occurrence, Semantics.NONE);
				out.add(last);
			}
			if (width == null && v.whens().stream().anyMatch(w -> w.comps().isEmpty() && w.nested() == null)) {
				bits = bodies.stream().allMatch(b -> b.bits() != null && b.bits() == 0) ? Long.valueOf(0) : null;
			}
			return new Built(last, bits, byteMultiple);
		}

		/** The test that the discriminant has a value, or one in a range. */
		private Expression test(Expression discriminant, Choice c, Discr d, int line) {
			if (discriminant.isBoolean()) {
				BigInteger v = choiceValue(c.value(), d, line);
				return v.signum() != 0 ? discriminant : new Expression.Unary(Expression.Op.NOT, discriminant);
			}
			Expression lo = new Expression.IntLiteral(choiceValue(c.value(), d, line).longValueExact());
			if (c.hi() == null) {
				return new Expression.Binary(Expression.Op.EQ, discriminant, lo);
			}
			Expression hi = new Expression.IntLiteral(choiceValue(c.hi(), d, line).longValueExact());
			return new Expression.Binary(Expression.Op.AND, new Expression.Binary(Expression.Op.GE, discriminant, lo),
					new Expression.Binary(Expression.Op.LE, discriminant, hi));
		}

		/** The value a choice names: an enumeration literal's code, a number, or a character's code. */
		private BigInteger choiceValue(Node n, Discr d, int line) {
			Ty t = view(d.type(), d.line()).base();
			if (n instanceof Name name && t instanceof EnumTy e) {
				BigInteger code = e.code(name.joined());
				if (code == null) {
					throw new EastException(line, "'" + name.raw() + "' isn't a value of "
							+ d.type().toUpperCase(Locale.ROOT));
				}
				return code;
			}
			if (n instanceof Chr c) {
				return BigInteger.valueOf(c.value());
			}
			BigDecimal v = EastParser.staticValue(logical, n);
			if (v == null || !EastParser.isWhole(v)) {
				throw new EastException(line, "A variant must be chosen by values of "
						+ d.type().toUpperCase(Locale.ROOT));
			}
			return v.toBigIntegerExact();
		}

		private static String alternativeName(When w, Discr d) {
			List<String> parts = new ArrayList<>();
			for (Choice c : w.choices()) {
				if (c.others()) {
					parts.add("others");
				} else {
					String lo = valueName(c.value());
					parts.add(c.hi() == null ? lo : lo + "_to_" + valueName(c.hi()));
				}
			}
			return "when_" + String.join("_or_", parts);
		}

		private static String valueName(Node n) {
			if (n instanceof Name name) {
				return name.last();
			}
			if (n instanceof Num num) {
				return num.value().toPlainString().replace("-", "minus_").replace('.', '_');
			}
			if (n instanceof Chr c) {
				return "char_" + (int) c.value();
			}
			return "value";
		}

		// -------------------------------------------------- expressions in the data

		/**
		 * An EAST expression as a description-model expression: discriminants
		 * become what they stand for, constants and enumeration literals their
		 * values, and EAST paths references to fields read earlier.
		 */
		private Expression expression(Node n, Ctx ctx, int line) {
			BigDecimal constant = EastParser.staticValue(logical, n);
			if (constant != null) {
				if (!EastParser.isWhole(constant)) {
					throw new EastException(line, "Only whole numbers can be used in the data's expressions, not "
							+ constant.toPlainString());
				}
				return new Expression.IntLiteral(constant.longValueExact());
			}
			if (n instanceof Name name) {
				if (name.path().size() == 1) {
					Expression bound = ctx.bindings().get(name.last());
					if (bound != null) {
						return bound;
					}
					for (Ty t : logical.types.values()) {
						if (t instanceof EnumTy e && e.literals.contains(name.last())) {
							return new Expression.IntLiteral(e.code(name.last()).longValueExact());
						}
					}
					return new Expression.FieldRef(name.last());
				}
				if (name.path().get(0).equals("ascii") && name.path().size() == 2) {
					Integer code = EastParser.ASCII.get(name.last());
					if (code == null) {
						throw new EastException(line, "'" + name.raw() + "' isn't one of the ASCII constants");
					}
					return new Expression.IntLiteral(code);
				}
				// An EAST path names a field from the top; from inside the records it goes through, only
				// the rest of the path is needed: a field of an enclosing record, or one inside a record
				// read earlier (header.length).
				for (int i = name.path().size() - 1; i > 0; i--) {
					if (ctx.ancestors().contains(String.join(".", name.path().subList(0, i)))) {
						return new Expression.FieldRef(String.join(".", name.path().subList(i, name.path().size())));
					}
				}
				return new Expression.FieldRef(name.joined());
			}
			if (n instanceof Chr c) {
				return new Expression.IntLiteral(c.value());
			}
			if (n instanceof Str s) {
				return new Expression.StringLiteral(s.value());
			}
			if (n instanceof Un u) {
				Expression operand = expression(u.operand(), ctx, line);
				return switch (u.op()) {
					case "-" -> new Expression.Unary(Expression.Op.NEG, operand);
					case "+" -> operand;
					case "not" -> new Expression.Unary(Expression.Op.NOT, operand);
					default -> throw new EastException(u.line(), "'" + u.op() + "' isn't supported in the data's "
							+ "expressions yet");
				};
			}
			if (n instanceof Bin b) {
				Expression.Op op = switch (b.op()) {
					case "+" -> Expression.Op.ADD;
					case "-" -> Expression.Op.SUB;
					case "*" -> Expression.Op.MUL;
					case "/" -> Expression.Op.DIV;
					case "mod", "rem" -> Expression.Op.MOD;
					case "=" -> Expression.Op.EQ;
					case "/=" -> Expression.Op.NE;
					case "<" -> Expression.Op.LT;
					case "<=" -> Expression.Op.LE;
					case ">" -> Expression.Op.GT;
					case ">=" -> Expression.Op.GE;
					case "and" -> Expression.Op.AND;
					case "or" -> Expression.Op.OR;
					default -> throw new EastException(b.line(), "'" + b.op() + "' on values from the data isn't "
							+ "supported yet");
				};
				return new Expression.Binary(op, expression(b.left(), ctx, line), expression(b.right(), ctx, line));
			}
			if (n instanceof Call call) {
				throw new EastException(call.line(), "The EAST operator '" + call.function()
						+ "' isn't supported in the data's expressions yet");
			}
			throw new EastException(line, "This value can't be used in the data's expressions");
		}

		private static Integer advance(Integer phase, Built b) {
			if (phase == null) {
				return null;
			}
			if (b.bits() != null) {
				return (int) ((phase + b.bits()) % 8);
			}
			return b.byteMultiple() ? phase : null;
		}

		private static ElementDescription repeat(ElementDescription e, Occurrence occurrence) {
			if (!(e.occurrence() instanceof Occurrence.Once)) {
				e = new RecordDescription(ElementDescription.newId(), e.name(), List.of(e), null, Occurrence.ONCE,
						Semantics.NONE);
			}
			if (e instanceof FieldDescription f) {
				return f.withOccurrence(occurrence);
			}
			if (e instanceof RecordDescription r) {
				return r.withOccurrence(occurrence);
			}
			return ((ChoiceDescription) e).withOccurrence(occurrence);
		}
	}

}
