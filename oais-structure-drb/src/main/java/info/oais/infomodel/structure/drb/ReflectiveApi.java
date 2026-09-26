package info.oais.infomodel.structure.drb;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import info.oais.infomodel.structure.StructureInterpretationException;

/**
 * Small reflection helpers shared by the DRB bridge classes.
 *
 * <p>This module talks to DRB purely through {@link java.lang.reflect}
 * rather than compiling against {@code fr.gael.drb} directly - see this
 * module's README for why. Because of that, every call site here is
 * necessarily a best-effort guess at DRB's actual method names, made from
 * DRB's long-public node-tree API shape (a node has a name, an optional
 * value, a list of children, a list of attributes; the entry point is a
 * factory resolver that turns a stream into a root node) rather than from
 * a javadoc this project could fetch and pin against. {@link #invokeFirst}
 * tries several plausible names in turn precisely so that a small mismatch
 * in your installed DRB version's exact method names does not require
 * touching {@link DrbStructureNode} or {@link DrbStructureRepInfo} - only
 * the candidate name lists here.
 */
final class ReflectiveApi {

	private ReflectiveApi() {
	}

	/**
	 * Invokes the first zero-argument method (by name) that both exists on
	 * {@code target}'s class <em>and</em> returns a non-null result. A
	 * candidate whose method exists but returns {@code null} for this
	 * particular target (e.g. a node's {@code getNamespaceUri()} on an
	 * unnamespaced node) is treated the same as a missing method and skipped
	 * in favour of the next candidate, rather than being taken as the final
	 * answer - otherwise the earlier, more commonly-present candidate would
	 * permanently mask a later, more specific one.
	 */
	static Optional<Object> invokeFirst(Object target, String... candidateMethodNames) {
		for (String name : candidateMethodNames) {
			try {
				Method m = target.getClass().getMethod(name);
				m.setAccessible(true);
				Optional<Object> result = Optional.ofNullable(m.invoke(target));
				if (result.isPresent()) {
					return result;
				}
				// method exists but returned nothing useful for this target - try the next candidate too
			} catch (NoSuchMethodException e) {
				// try the next candidate
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new StructureInterpretationException(
						"DRB node method '" + name + "' on " + target.getClass() + " could not be invoked", e);
			}
		}
		return Optional.empty();
	}

	/**
	 * Normalises the result of a "give me the children/attributes" call -
	 * which different DRB versions/node kinds may return as a
	 * {@link java.util.List}, an array, or (via an iterator-returning
	 * method) an {@link java.util.Iterator} - into a plain {@link List}.
	 */
	@SuppressWarnings("unchecked")
	static List<Object> asList(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof List<?> list) {
			return (List<Object>) list;
		}
		if (value.getClass().isArray()) {
			int length = Array.getLength(value);
			List<Object> result = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				result.add(Array.get(value, i));
			}
			return result;
		}
		if (value instanceof Iterable<?> iterable) {
			List<Object> result = new ArrayList<>();
			for (Object o : iterable) {
				result.add(o);
			}
			return result;
		}
		throw new StructureInterpretationException(
				"Expected a List, array or Iterable of DRB nodes/attributes but got " + value.getClass());
	}
}
