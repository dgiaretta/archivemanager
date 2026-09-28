package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.Objects;

/** How many times an element occurs where it's declared. */
public sealed interface Occurrence extends Serializable {

	/** Exactly once - the default. */
	Occurrence ONCE = new Once();

	record Once() implements Occurrence {
	}

	/** Once when {@code condition} holds, otherwise absent. */
	record Optional(Expression condition) implements Occurrence {
		public Optional {
			Objects.requireNonNull(condition, "condition");
		}
	}

	/** {@code count} times - a literal or computed from earlier fields. */
	record Repeated(Expression count) implements Occurrence {
		public Repeated {
			Objects.requireNonNull(count, "count");
		}
	}

	/** Repeated until the end of the data (or of the enclosing record's data). */
	record UntilEnd() implements Occurrence {
	}

	/** Whether this element can produce more than one node. */
	default boolean isRepeated() {
		return this instanceof Repeated || this instanceof UntilEnd;
	}
}
