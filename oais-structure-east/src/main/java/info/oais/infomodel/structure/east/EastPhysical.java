package info.oais.infomodel.structure.east;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.oais.infomodel.structure.east.EastParser.Agg;
import info.oais.infomodel.structure.east.EastParser.Choice;
import info.oais.infomodel.structure.east.EastParser.Comp;
import info.oais.infomodel.structure.east.EastParser.Const;
import info.oais.infomodel.structure.east.EastParser.Name;
import info.oais.infomodel.structure.east.EastParser.Node;
import info.oais.infomodel.structure.east.EastParser.Pkg;
import info.oais.infomodel.structure.east.EastParser.RecTy;
import info.oais.infomodel.structure.east.EastParser.Str;
import info.oais.infomodel.structure.east.EastParser.SubTy;
import info.oais.infomodel.structure.east.EastParser.When;

/**
 * What a physical package says (CCSDS 644.0-B-3 section 3.3): the order of
 * octets and bits, the order arrays are stored in, and the representation of
 * each scalar type it names in its {@code RELATION} record.
 */
final class EastPhysical {

	/** A representation: one of the physical description record types, and its value. */
	record Rep(String kind, Agg value, int line) {

		Node get(String component, int index) {
			return value.get(component, index);
		}

		long number(Pkg physical, String component, int index) {
			Node n = get(component, index);
			BigDecimal v = n == null ? null : EastParser.staticValue(physical, n);
			if (v == null) {
				throw new EastException(line, "The representation needs " + component.toUpperCase(java.util.Locale.ROOT));
			}
			return v.longValue();
		}

		String name(String component, int index) {
			return get(component, index) instanceof Name n ? n.joined() : "";
		}

		/** A LOCATION_OF_FIELD value: the subfields, each a first and last bit number. */
		List<long[]> location(Pkg physical, String component, int index) {
			List<long[]> out = new ArrayList<>();
			Node n = get(component, index);
			if (!(n instanceof Agg subfields)) {
				return out;
			}
			for (Agg.Item item : subfields.items()) {
				if (!(item.value() instanceof Agg range)) {
					throw new EastException(line, "Each subfield of " + component.toUpperCase(java.util.Locale.ROOT)
							+ " must be (first bit, last bit)");
				}
				BigDecimal first = EastParser.staticValue(physical, range.get("beginning_at_bit_number", 0));
				BigDecimal last = EastParser.staticValue(physical, range.get("ending_at_bit_number", 1));
				if (first == null || last == null) {
					throw new EastException(line, "Each subfield of " + component.toUpperCase(java.util.Locale.ROOT)
							+ " must be two bit numbers");
				}
				out.add(new long[] {first.longValue(), last.longValue()});
			}
			return out;
		}

		/** The strings of an ASCII_ENUMERATION_PHYSICAL_DESCRIPTION, one per literal. */
		List<String> strings() {
			List<String> out = new ArrayList<>();
			if (get("representation", 2) instanceof Agg list) {
				for (Agg.Item item : list.items()) {
					out.add(item.value() instanceof Str s ? s.value() : null);
				}
			}
			return out;
		}
	}

	final Pkg physical;
	/** OCTET_STORAGE = LOW_ORDER_FIRST: little-endian, bits least significant first. */
	final boolean lowOrderFirst;
	/** ARRAY_STORAGE = LAST_INDEX_FIRST: the last index of an array varies fastest. */
	final boolean lastIndexFirst;
	private final Map<String, Rep> reps = new HashMap<>();

	EastPhysical(Pkg physical) {
		this.physical = physical;
		boolean low = false;
		boolean last = false;
		if (physical != null) {
			Const octets = physical.constants.get("octet_storage");
			if (octets != null && octets.value() instanceof Name n) {
				if (n.joined().equals("low_order_first")) {
					low = true;
				} else if (!n.joined().equals("high_order_first")) {
					throw new EastException(octets.line(), "OCTET_STORAGE must be HIGH_ORDER_FIRST or LOW_ORDER_FIRST");
				}
			}
			Const arrays = physical.constants.get("array_storage");
			if (arrays != null && arrays.value() instanceof Name n) {
				last = n.joined().equals("last_index_first");
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
		this.lowOrderFirst = low;
		this.lastIndexFirst = last;
	}

	/** The representation the physical package gives a type, or one it's a subtype of; null if none. */
	Rep repFor(Pkg logical, String typeName) {
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
}
