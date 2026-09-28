package info.oais.infomodel.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where an element is in a decoded structure tree, below its root: element
 * names separated by {@code /}, each optionally followed by a 1-based index
 * among its same-named siblings, e.g. {@code /header/sample[2]/value}. A step
 * without an index means the first (usually only) element of that name.
 * Used to say which values to change when writing data back (see
 * {@link WritableStructureRepInfo}).
 *
 * @param steps the path's steps, outermost first; never empty
 */
public record ElementPath(List<Step> steps) {

	private static final Pattern STEP = Pattern.compile("([^/\\[\\]\\s]+)(?:\\[(\\d+)\\])?");

	/**
	 * One step of a path.
	 *
	 * @param name  the element's name
	 * @param index its 1-based position among the siblings called {@code name}
	 */
	public record Step(String name, int index) {
		public Step {
			if (name == null || name.isEmpty()) {
				throw new IllegalArgumentException("A path step needs a name");
			}
			if (index < 1) {
				throw new IllegalArgumentException("Path indexes start at 1: " + name + "[" + index + "]");
			}
		}

		@Override
		public String toString() {
			return index == 1 ? name : name + "[" + index + "]";
		}
	}

	public ElementPath {
		if (steps == null || steps.isEmpty()) {
			throw new IllegalArgumentException("An element path needs at least one step");
		}
		steps = List.copyOf(steps);
	}

	/**
	 * Parses {@code /a/b[2]/c}; the leading {@code /} is optional.
	 *
	 * @throws IllegalArgumentException if {@code text} isn't a path
	 */
	public static ElementPath parse(String text) {
		String s = text == null ? "" : text.strip();
		if (s.startsWith("/")) {
			s = s.substring(1);
		}
		if (s.isEmpty()) {
			throw new IllegalArgumentException("An element path needs at least one step, e.g. /header/count");
		}
		List<Step> steps = new ArrayList<>();
		for (String part : s.split("/", -1)) {
			Matcher m = STEP.matcher(part.strip());
			if (!m.matches()) {
				throw new IllegalArgumentException("'" + part + "' isn't a path step: write a name, optionally "
						+ "followed by [n], e.g. sample[2]");
			}
			steps.add(new Step(m.group(1), m.group(2) == null ? 1 : Integer.parseInt(m.group(2))));
		}
		return new ElementPath(steps);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Step step : steps) {
			sb.append('/').append(step);
		}
		return sb.toString();
	}

	/**
	 * The node this path names below {@code root}, in a decoded tree.
	 *
	 * @throws IllegalArgumentException if there's no such node
	 */
	public StructureNode find(StructureNode root) {
		StructureNode current = root;
		for (Step step : steps) {
			List<StructureNode> named = current.childrenNamed(step.name());
			if (named.size() < step.index()) {
				throw new IllegalArgumentException(notFound(step, named.size()));
			}
			current = named.get(step.index() - 1);
		}
		return current;
	}

	/** The message for a step that names no element: how many there are instead. */
	public String notFound(Step step, int count) {
		return "There's no element " + this + ": " + (count == 0 ? "nothing is called '" + step.name() + "' there"
				: "there " + (count == 1 ? "is only 1" : "are only " + count) + " called '" + step.name() + "'");
	}
}
