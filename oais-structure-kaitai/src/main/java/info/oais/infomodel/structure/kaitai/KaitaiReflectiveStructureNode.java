package info.oais.infomodel.structure.kaitai;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.kaitai.struct.KaitaiStruct;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Wraps a Kaitai-Struct-generated object graph as a {@link StructureNode}
 * tree, generically - i.e. without knowing anything about the particular
 * generated class - by reflecting over the accessor methods every Kaitai
 * Java target class has, whatever format it was compiled from.
 *
 * <p>This relies on three conventions documented for Kaitai Struct's Java
 * code generator:</p>
 *
 * <ul>
 * <li>each parsed field is exposed as a public, zero-argument, non-static
 * method named after the field in camelCase, with <em>no</em> {@code get}
 * prefix (e.g. a {@code label_len} field in the {@code .ksy} becomes a
 * {@code labelLen()} method, not {@code getLabelLen()});</li>
 * <li>a nested/sub-type field's accessor returns another
 * {@link KaitaiStruct} subclass, which is walked the same way, recursively;</li>
 * <li>a repeated ({@code repeat:}) field's accessor returns a
 * {@link java.util.List}.</li>
 * </ul>
 *
 * <p>so a single implementation here works for any {@code .ksy}-derived
 * class without per-format code. Field methods declared directly by
 * {@link KaitaiStruct} itself, or matching common stream/bookkeeping names
 * ({@code _io}, {@code _parent}, {@code _root}), are excluded; see
 * {@link #isFieldAccessor(Method)}.</p>
 *
 * <p><b>Source ranges ({@link #getSourceRange()}):</b> available for classes
 * compiled with {@code ksc --debug}. As verified against the Kaitai Struct
 * compiler 0.11, such a class carries public {@code Map<String, Integer>
 * _attrStart}/{@code _attrEnd} fields - each field's start and end byte
 * offsets, keyed by its Java accessor name (e.g. {@code "labelLen"}) - and
 * {@code Map<String, List<Integer>> _arrStart}/{@code _arrEnd} with one
 * entry per element of a repeated field. A class compiled without
 * {@code --debug} has none of these, and every node simply reports no source
 * range. (A debug build also doesn't parse in its constructor; see
 * {@link KaitaiStructureRepInfo}.)</p>
 */
public final class KaitaiReflectiveStructureNode implements StructureNode {

	private static final Set<String> RESERVED_METHOD_NAMES =
			Set.of("toString", "hashCode", "equals", "_io", "_parent", "_root", "_read", "_fetchInstances");

	private final String name;
	private final Object value;
	private final Class<?> declaredType;
	private final Optional<ByteRange> sourceRange;
	/** For a repeated field: each element's range, from its parent's {@code _arrStart}/{@code _arrEnd}. */
	private final List<Optional<ByteRange>> elementRanges;
	private final Map<String, Object> attributes;

	private KaitaiReflectiveStructureNode(String name, Object value, Class<?> declaredType,
			Optional<ByteRange> sourceRange, List<Optional<ByteRange>> elementRanges) {
		this.name = name;
		this.value = value;
		this.declaredType = declaredType;
		this.sourceRange = sourceRange;
		this.elementRanges = elementRanges;
		this.attributes = Map.of();
	}

	private KaitaiReflectiveStructureNode(String name, KaitaiStruct root, Map<String, Object> attributes) {
		this.name = name;
		this.value = root;
		this.declaredType = root.getClass();
		this.sourceRange = Optional.empty();
		this.elementRanges = List.of();
		this.attributes = Map.copyOf(attributes);
	}

	/**
	 * Wraps the root of a parsed Kaitai object graph.
	 *
	 * @param name             the name to give the root node (the input has none of its own)
	 * @param kaitaiStructRoot the object returned by a generated class's constructor
	 * @return a {@link StructureNode} view over it
	 */
	/** Like {@link #ofRoot(String, KaitaiStruct)}, with attributes on the root (e.g. {@link StructureNode#TRAILING_BYTES}). */
	public static StructureNode ofRoot(String name, KaitaiStruct kaitaiStructRoot, Map<String, Object> attributes) {
		return new KaitaiReflectiveStructureNode(name, kaitaiStructRoot, attributes);
	}

	public static StructureNode ofRoot(String name, KaitaiStruct kaitaiStructRoot) {
		return new KaitaiReflectiveStructureNode(name, kaitaiStructRoot, kaitaiStructRoot.getClass(), Optional.empty(),
				List.of());
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Optional<String> getTypeName() {
		if (value instanceof KaitaiStruct) {
			// A Kaitai switch-type (`type: switch-on`) field's accessor is declared
			// against a common supertype, but the actual parsed object is one of
			// several concrete subtypes chosen at parse time - report which one was
			// actually parsed, not the declared common type, which would otherwise
			// be the one case where this name is least informative.
			return Optional.of(value.getClass().getSimpleName());
		}
		return Optional.of(declaredType.getSimpleName());
	}

	@Override
	public StructureNodeKind getKind() {
		if (value instanceof KaitaiStruct) {
			return StructureNodeKind.COMPOSITE;
		}
		if (value instanceof List) {
			return StructureNodeKind.ARRAY;
		}
		return StructureNodeKind.LEAF;
	}

	@Override
	public Optional<Object> getValue() {
		if (getKind() != StructureNodeKind.LEAF) {
			return Optional.empty();
		}
		return Optional.ofNullable(value);
	}

	@Override
	public List<StructureNode> getChildren() {
		if (value instanceof KaitaiStruct struct) {
			Map<?, ?> attrStart = debugMap(struct, "_attrStart");
			Map<?, ?> attrEnd = debugMap(struct, "_attrEnd");
			Map<?, ?> arrStart = debugMap(struct, "_arrStart");
			Map<?, ?> arrEnd = debugMap(struct, "_arrEnd");
			return fieldAccessors(struct.getClass())
					.map(m -> {
						String field = m.getName();
						Object fieldValue = invoke(m, struct);
						Optional<ByteRange> range = range(attrStart.get(field), attrEnd.get(field));
						List<Optional<ByteRange>> elementRanges = new ArrayList<>();
						if (fieldValue instanceof List<?> list && arrStart.get(field) instanceof List<?> starts
								&& arrEnd.get(field) instanceof List<?> ends) {
							for (int i = 0; i < list.size(); i++) {
								elementRanges.add(i < starts.size() && i < ends.size()
										? range(starts.get(i), ends.get(i)) : Optional.empty());
							}
						}
						return (StructureNode) new KaitaiReflectiveStructureNode(field, fieldValue, m.getReturnType(),
								range, elementRanges);
					})
					.collect(Collectors.toList());
		}
		if (value instanceof List<?> list) {
			List<StructureNode> children = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				Object element = list.get(i);
				Class<?> elementType = element == null ? Object.class : element.getClass();
				Optional<ByteRange> elementRange = i < elementRanges.size() ? elementRanges.get(i) : Optional.empty();
				children.add(new KaitaiReflectiveStructureNode(String.valueOf(i), element, elementType, elementRange,
						List.of()));
			}
			return children;
		}
		return List.of();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Optional<ByteRange> getSourceRange() {
		return sourceRange;
	}

	/**
	 * One of the position maps a {@code ksc --debug} class declares as a public
	 * field (see this class's Javadoc); empty for any other class.
	 */
	private static Map<?, ?> debugMap(Object struct, String fieldName) {
		try {
			Field f = struct.getClass().getField(fieldName);
			if (f.get(struct) instanceof Map<?, ?> m) {
				return m;
			}
		} catch (NoSuchFieldException | IllegalAccessException e) {
			// Not compiled with --debug: no source ranges available.
		}
		return Map.of();
	}

	private static Optional<ByteRange> range(Object start, Object end) {
		if (start instanceof Number s && end instanceof Number e && e.longValue() >= s.longValue()) {
			return Optional.of(ByteRange.ofBytes(s.longValue(), e.longValue() - s.longValue()));
		}
		return Optional.empty();
	}

	/**
	 * All accessor methods on {@code type} (and its Kaitai-generated
	 * superclasses, for nested-type inheritance, though that is rare in
	 * generated code) that represent a parsed field, per the conventions
	 * described in this class's Javadoc.
	 */
	private static java.util.stream.Stream<Method> fieldAccessors(Class<?> type) {
		List<Method> methods = new ArrayList<>();
		for (Class<?> c = type; c != null && KaitaiStruct.class.isAssignableFrom(c) && c != KaitaiStruct.class;
				c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (isFieldAccessor(m)) {
					methods.add(m);
				}
			}
		}
		// Reflection returns methods in no particular order, but a format's fields
		// have one: file order.
		List<String> order = fieldOrder(type);
		methods.sort(java.util.Comparator.comparingInt(m -> {
			int i = order.indexOf(m.getName());
			return i < 0 ? Integer.MAX_VALUE : i;
		}));
		return methods.stream();
	}

	/**
	 * The file order of {@code type}'s fields: its {@code _seqFields} (declared
	 * by {@code ksc --debug} builds, in {@code seq} order), otherwise its Java
	 * fields in declaration order, which the compiler also writes in
	 * {@code seq} order. Computed ({@code instances}) values follow.
	 */
	private static List<String> fieldOrder(Class<?> type) {
		try {
			if (type.getField("_seqFields").get(null) instanceof String[] seq) {
				return List.of(seq);
			}
		} catch (NoSuchFieldException | IllegalAccessException | NullPointerException e) {
			// Not a --debug build: fall back to field declaration order.
		}
		List<String> names = new ArrayList<>();
		for (Field f : type.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers()) && !f.getName().startsWith("_")) {
				names.add(f.getName());
			}
		}
		return names;
	}

	private static boolean isFieldAccessor(Method m) {
		return Modifier.isPublic(m.getModifiers())
				&& !Modifier.isStatic(m.getModifiers())
				&& m.getParameterCount() == 0
				&& !m.isSynthetic()
				&& !m.isBridge()
				&& !void.class.equals(m.getReturnType())
				&& !RESERVED_METHOD_NAMES.contains(m.getName());
	}

	private static Object invoke(Method m, Object target) {
		try {
			m.setAccessible(true);
			return m.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new StructureInterpretationException(
					"Unable to read Kaitai-generated field '" + m.getName() + "' on " + target.getClass(), e);
		}
	}
}
