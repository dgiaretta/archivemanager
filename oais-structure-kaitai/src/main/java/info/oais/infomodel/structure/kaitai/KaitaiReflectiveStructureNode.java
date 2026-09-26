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
 * <p><b>Source ranges ({@link #getSourceRange()}):</b> populated when the
 * struct a field was read from carries a public {@code Map<String, Object>
 * _debug} field, keyed by field id, each entry itself a map with (at least)
 * "start" and "end" byte offsets and - for a repeated field - an "arr" list
 * of one such map per element. This is the shape the real Kaitai Struct
 * compiler produces when a {@code .ksy} is compiled with {@code ksc --debug}
 * (used e.g. by the Kaitai Web IDE for hex-highlighting); see
 * {@code generated/Point2d.java} for a hand-written class following the same
 * convention, since this project could not run the real compiler with
 * {@code --debug} to confirm the exact key naming it uses. For that reason
 * {@link #fieldDebugEntry} tries a field's Java accessor name first (e.g.
 * {@code "labelLen"}) and its snake_case form second (e.g.
 * {@code "label_len"}) - whichever convention your compiler version
 * actually uses, one of the two should match. A class with no {@code
 * _debug} field (i.e. compiled without {@code --debug}, or a hand-written
 * stand-in that does not populate one) simply reports no source ranges,
 * exactly as before this was added.</p>
 */
public final class KaitaiReflectiveStructureNode implements StructureNode {

	private static final Set<String> RESERVED_METHOD_NAMES =
			Set.of("toString", "hashCode", "equals", "_io", "_parent", "_root", "_read", "_fetchInstances");

	private final String name;
	private final Object value;
	private final Class<?> declaredType;
	private final Optional<Map<?, ?>> debugEntry;

	private KaitaiReflectiveStructureNode(String name, Object value, Class<?> declaredType,
			Optional<Map<?, ?>> debugEntry) {
		this.name = name;
		this.value = value;
		this.declaredType = declaredType;
		this.debugEntry = debugEntry;
	}

	/**
	 * Wraps the root of a parsed Kaitai object graph.
	 *
	 * @param name             the name to give the root node (the input has none of its own)
	 * @param kaitaiStructRoot the object returned by a generated class's constructor
	 * @return a {@link StructureNode} view over it
	 */
	public static StructureNode ofRoot(String name, KaitaiStruct kaitaiStructRoot) {
		return new KaitaiReflectiveStructureNode(name, kaitaiStructRoot, kaitaiStructRoot.getClass(), Optional.empty());
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
			Optional<Map<?, ?>> debug = debugMapOf(struct);
			return fieldAccessors(struct.getClass())
					.map(m -> {
						Object fieldValue = invoke(m, struct);
						Optional<Map<?, ?>> entry = debug.flatMap(dm -> fieldDebugEntry(dm, m.getName()));
						return (StructureNode) new KaitaiReflectiveStructureNode(m.getName(), fieldValue, m.getReturnType(),
								entry);
					})
					.collect(Collectors.toList());
		}
		if (value instanceof List<?> list) {
			List<?> elementEntries = debugEntry
					.map(e -> e.get("arr"))
					.filter(a -> a instanceof List)
					.map(a -> (List<?>) a)
					.orElse(null);
			List<StructureNode> children = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				Object element = list.get(i);
				Class<?> elementType = element == null ? Object.class : element.getClass();
				Optional<Map<?, ?>> elementDebug =
						(elementEntries != null && i < elementEntries.size() && elementEntries.get(i) instanceof Map<?, ?> em)
								? Optional.of(em)
								: Optional.empty();
				children.add(new KaitaiReflectiveStructureNode(String.valueOf(i), element, elementType, elementDebug));
			}
			return children;
		}
		return List.of();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return Map.of();
	}

	@Override
	public Optional<ByteRange> getSourceRange() {
		return debugEntry.flatMap(KaitaiReflectiveStructureNode::byteRangeFrom);
	}

	/**
	 * The {@code _debug} field a class compiled with {@code ksc --debug} (or a
	 * hand-written stand-in following the same convention) carries, describing
	 * the byte ranges of its own direct fields. Absent for anything else, in
	 * which case every field of {@code struct} simply reports no source range.
	 */
	private static Optional<Map<?, ?>> debugMapOf(Object struct) {
		try {
			Field f = struct.getClass().getField("_debug");
			Object v = f.get(struct);
			if (v instanceof Map<?, ?> m) {
				return Optional.of(m);
			}
		} catch (NoSuchFieldException e) {
			// Not compiled with --debug (or a hand-written stand-in without one) - no source ranges available.
		} catch (IllegalAccessException e) {
			// _debug is documented as a public field; if some Kaitai version makes it otherwise, degrade quietly.
		}
		return Optional.empty();
	}

	private static Optional<Map<?, ?>> fieldDebugEntry(Map<?, ?> debugMap, String javaFieldName) {
		Object entry = debugMap.get(javaFieldName);
		if (entry == null) {
			entry = debugMap.get(toSnakeCase(javaFieldName));
		}
		return (entry instanceof Map<?, ?> m) ? Optional.of(m) : Optional.empty();
	}

	private static Optional<ByteRange> byteRangeFrom(Map<?, ?> entry) {
		Object start = entry.get("start");
		Object end = entry.get("end");
		if (start instanceof Number s && end instanceof Number e && e.longValue() >= s.longValue()) {
			return Optional.of(ByteRange.ofBytes(s.longValue(), e.longValue() - s.longValue()));
		}
		return Optional.empty();
	}

	private static String toSnakeCase(String camelCase) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < camelCase.length(); i++) {
			char c = camelCase.charAt(i);
			if (Character.isUpperCase(c)) {
				sb.append('_').append(Character.toLowerCase(c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
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
		return methods.stream();
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
