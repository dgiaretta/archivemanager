package info.oais.infomodel.structure.description;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the field references in an element's expressions (its length,
 * condition, repeat count, or a choice's discriminator).
 *
 * <p>A reference names a field read <em>earlier</em>: an earlier sibling of
 * the referring element in its record, or else an earlier child of an
 * enclosing record, searching outwards - the nearest match wins. The target
 * must be a field that occurs exactly once, so its value is always there.
 * These are the same rules Kaitai Struct, DFDL and DRB can all express, which
 * is what lets one expression be generated for all of them.</p>
 */
public final class Scope {

	/**
	 * A resolved reference.
	 *
	 * @param target   the field referred to
	 * @param nodesUp  how many data-tree levels up from the referring element's own node
	 *                 the node holding {@code target} is - the number of {@code ..} steps in
	 *                 a DFDL or DRB path (each record, choice and branch is one node)
	 * @param typesUp  how many record types up the target is - the number of
	 *                 {@code _parent} steps in Kaitai Struct (a choice's branch record's
	 *                 parent is the record declaring the choice)
	 */
	public record Resolved(FieldDescription target, int nodesUp, int typesUp) {
	}

	private final Map<String, List<Step>> pathsById;

	/** One ancestor on the way from the root down to an element. */
	private record Step(ElementDescription container, List<ElementDescription> earlierSiblings) {
	}

	public Scope(FormatDescription format) {
		this.pathsById = new java.util.HashMap<>();
		index(format.root(), new ArrayList<>());
	}

	private void index(RecordDescription record, List<Step> path) {
		List<ElementDescription> children = record.children();
		for (int i = 0; i < children.size(); i++) {
			ElementDescription child = children.get(i);
			List<Step> childPath = new ArrayList<>(path);
			childPath.add(new Step(record, children.subList(0, i)));
			pathsById.put(child.id(), childPath);
			if (child instanceof RecordDescription r) {
				index(r, childPath);
			} else if (child instanceof ChoiceDescription c) {
				for (ChoiceDescription.Branch b : c.branches()) {
					List<Step> branchPath = new ArrayList<>(childPath);
					branchPath.add(new Step(c, List.of()));
					pathsById.put(b.record().id(), branchPath);
					index(b.record(), branchPath);
				}
			}
		}
	}

	/** Resolves {@code name} as referred to from the element with id {@code fromId}. */
	public Optional<Resolved> resolve(String fromId, String name) {
		List<Step> path = pathsById.get(fromId);
		if (path == null) {
			return Optional.empty();
		}
		int nodesUp = 0;
		int typesUp = 0;
		for (int i = path.size() - 1; i >= 0; i--) {
			Step step = path.get(i);
			nodesUp++;
			if (step.container() instanceof RecordDescription) {
				for (int j = step.earlierSiblings().size() - 1; j >= 0; j--) {
					ElementDescription sibling = step.earlierSiblings().get(j);
					if (sibling.name().equals(name)) {
						return sibling instanceof FieldDescription f && f.occurrence() instanceof Occurrence.Once
								? Optional.of(new Resolved(f, nodesUp, typesUp))
								: Optional.empty();
					}
				}
				// Leaving a record for its container. A branch record's Kaitai type sits
				// directly under the record declaring the choice, so the choice node that
				// follows adds a data-tree level but not a type level.
				typesUp++;
			}
		}
		return Optional.empty();
	}

	/** Whether the element with this id is somewhere in the description. */
	public boolean contains(String id) {
		return pathsById.containsKey(id);
	}

	/** The record or choice directly containing the element with this id; empty for the root. */
	public Optional<ElementDescription> parentOf(String id) {
		List<Step> path = pathsById.get(id);
		return path == null || path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1).container());
	}
}
