package info.oais.infomodel.structure.description;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Editing operations on a description tree, by element {@link ElementDescription#id() id}.
 * Descriptions are immutable, so each operation returns a new root.
 */
public final class Descriptions {

	private Descriptions() {
	}

	/** Every element in document order, including choice branches' records. */
	public static List<ElementDescription> all(RecordDescription root) {
		List<ElementDescription> out = new ArrayList<>();
		walk(root, out);
		return out;
	}

	private static void walk(ElementDescription e, List<ElementDescription> out) {
		out.add(e);
		if (e instanceof RecordDescription r) {
			r.children().forEach(c -> walk(c, out));
		} else if (e instanceof ChoiceDescription c) {
			c.branches().forEach(b -> walk(b.record(), out));
		}
	}

	public static Optional<ElementDescription> find(RecordDescription root, String id) {
		return all(root).stream().filter(e -> e.id().equals(id)).findFirst();
	}

	/** Replaces the element with {@code id} by what {@code change} returns for it. */
	public static RecordDescription update(RecordDescription root, String id, UnaryOperator<ElementDescription> change) {
		return (RecordDescription) rewrite(root, id, change);
	}

	private static ElementDescription rewrite(ElementDescription e, String id, UnaryOperator<ElementDescription> change) {
		if (e.id().equals(id)) {
			return change.apply(e);
		}
		if (e instanceof RecordDescription r) {
			return r.withChildren(r.children().stream().map(c -> rewrite(c, id, change)).toList());
		}
		if (e instanceof ChoiceDescription c) {
			return c.withBranches(c.branches().stream()
					.map(b -> new ChoiceDescription.Branch(b.key(), (RecordDescription) rewrite(b.record(), id, change)))
					.toList());
		}
		return e;
	}

	/** Adds {@code element} as the last child of the record with {@code recordId}. */
	public static RecordDescription addChild(RecordDescription root, String recordId, ElementDescription element) {
		return update(root, recordId, e -> {
			RecordDescription r = (RecordDescription) e;
			List<ElementDescription> children = new ArrayList<>(r.children());
			children.add(element);
			return r.withChildren(children);
		});
	}

	/** Adds a branch to the choice with {@code choiceId}. */
	public static RecordDescription addBranch(RecordDescription root, String choiceId, ChoiceDescription.Branch branch) {
		return update(root, choiceId, e -> {
			ChoiceDescription c = (ChoiceDescription) e;
			List<ChoiceDescription.Branch> branches = new ArrayList<>(c.branches());
			branches.add(branch);
			return c.withBranches(branches);
		});
	}

	/** Removes the element (or choice branch record) with {@code id}. The root can't be removed. */
	public static RecordDescription remove(RecordDescription root, String id) {
		return (RecordDescription) removeFrom(root, id);
	}

	private static ElementDescription removeFrom(ElementDescription e, String id) {
		if (e instanceof RecordDescription r) {
			return r.withChildren(r.children().stream().filter(c -> !c.id().equals(id))
					.map(c -> removeFrom(c, id)).toList());
		}
		if (e instanceof ChoiceDescription c) {
			return c.withBranches(c.branches().stream().filter(b -> !b.record().id().equals(id))
					.map(b -> new ChoiceDescription.Branch(b.key(), (RecordDescription) removeFrom(b.record(), id)))
					.toList());
		}
		return e;
	}

	/** Moves the element with {@code id} {@code delta} places within its record (or its branch within its choice). */
	public static RecordDescription move(RecordDescription root, String id, int delta) {
		return (RecordDescription) moveIn(root, id, delta);
	}

	private static ElementDescription moveIn(ElementDescription e, String id, int delta) {
		if (e instanceof RecordDescription r) {
			List<ElementDescription> children = new ArrayList<>(r.children());
			shift(children, indexOf(children.stream().map(ElementDescription::id).toList(), id), delta);
			return r.withChildren(children.stream().map(c -> moveIn(c, id, delta)).toList());
		}
		if (e instanceof ChoiceDescription c) {
			List<ChoiceDescription.Branch> branches = new ArrayList<>(c.branches());
			shift(branches, indexOf(branches.stream().map(b -> b.record().id()).toList(), id), delta);
			return c.withBranches(branches.stream()
					.map(b -> new ChoiceDescription.Branch(b.key(), (RecordDescription) moveIn(b.record(), id, delta)))
					.toList());
		}
		return e;
	}

	private static int indexOf(List<String> ids, String id) {
		return ids.indexOf(id);
	}

	private static <T> void shift(List<T> list, int index, int delta) {
		int target = index + delta;
		if (index >= 0 && target >= 0 && target < list.size()) {
			list.add(target, list.remove(index));
		}
	}
}
