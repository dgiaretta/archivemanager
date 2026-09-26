package info.oais.infomodel.structure.dfdl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.daffodil.japi.infoset.InfosetOutputter;
import org.apache.daffodil.runtime1.api.InfosetArray;
import org.apache.daffodil.runtime1.api.InfosetComplexElement;
import org.apache.daffodil.runtime1.api.InfosetSimpleElement;

import info.oais.infomodel.structure.ByteRange;

/**
 * A second, disposable {@link InfosetOutputter} run purely to recover
 * per-element bit positions for {@link DomStructureNode#getSourceRange()} -
 * see {@link DfdlStructureRepInfo#doApply} for how the two parses fit
 * together, and that class's Javadoc on {@code tryCapturePositions} for why
 * a parse failure here can never affect the primary, DOM-producing parse.
 *
 * <p><b>Why a second parse instead of getting this from the DOM parse
 * directly:</b> {@code W3CDOMInfosetOutputter} (used for the "real" parse
 * that produces the {@link info.oais.infomodel.structure.StructureNode}
 * tree applications see) is a finished, documented Daffodil class this
 * project already depends on with confidence; reaching into its internals
 * to correlate a built DOM {@code Element} back to a bit position was not
 * something this project could verify without a live Daffodil install to
 * test against, and getting that wrong risked breaking DOM parsing itself.
 * Running a second, completely independent parse with a purpose-built
 * outputter keeps that risk contained: if this class does not compile
 * cleanly against your Daffodil version - it depends on
 * {@code InfosetOutputter}'s abstract method set, which this project could
 * not confirm against a live compile, being what this project's author
 * believes it to be - that is a compile error in this one file to report
 * back and fix, exactly like the schema-property errors this project's
 * DFDL schema already went through; it does not put the working, tested
 * DOM-based parse path at risk.</p>
 *
 * <p><b>Why reflection for the position itself:</b> which method (if any)
 * {@link InfosetSimpleElement}/{@link InfosetComplexElement} expose a bit or
 * byte position through was not something this project could confirm
 * either, so - following the same pattern {@code oais-structure-drb} uses
 * for DRB's equally under-documented API, see {@code ReflectiveApi} there -
 * {@link #positionInBitsOf} tries a list of plausible candidate method
 * names and quietly gives up if none of them exist or all return null.
 * Adjust {@link #BIT_POSITION_METHOD_NAMES}/{@link #BYTE_POSITION_METHOD_NAMES}
 * for your Daffodil version if you find the real accessor and it is not
 * already listed - nothing else needs to change.</p>
 *
 * <p>Positions are correlated back to {@link DomStructureNode}s by
 * structural path (the sequence of child-element indices from the document
 * root), not by object identity, since the DOM tree and this tracker's
 * ranges are built by two independent parses of the same bytes. This only
 * requires both parses to visit elements in the same, deterministic
 * document order, which a single-threaded parse of the same bytes
 * guarantees. A DFDL array's repeated elements appear as flat, same-named
 * siblings in the DOM rather than nested under a wrapper (see
 * {@link DomStructureNode}'s Javadoc), so {@link #startArray}/
 * {@link #endArray} deliberately do not introduce a path level of their
 * own or capture a position - only {@link #startSimple}/
 * {@link #startComplex} do, matching one DOM element each.</p>
 *
 * <p><b>Typed values:</b> for the same reason {@code W3CDOMInfosetOutputter}
 * only ever hands back a DOM tree - and a DOM {@code Element}'s text content
 * is always a {@code String}, regardless of the DFDL schema's declared type -
 * {@link DomStructureNode#getValue()} could not, on its own, tell an
 * {@code xs:int} element from an {@code xs:string} one. This class also
 * captures each simple element's already-correctly-typed value at
 * {@link #endSimple}, via {@link #tryTypedValueOf}, the same best-effort,
 * reflective way {@link #positionInBitsOf} recovers positions: it tries
 * {@link InfosetSimpleElement#getClass()}'s {@code getObject()} (Daffodil's
 * documented typed-value accessor as of the version this project could
 * confirm against a real compile - see {@link #TYPED_VALUE_METHOD_NAMES})
 * and quietly gives up, leaving {@link DomStructureNode} to fall back to the
 * DOM's raw text, if that is not found or returns {@code null}.</p>
 */
final class PositionTrackingInfosetOutputter extends InfosetOutputter {

	private static final String[] BIT_POSITION_METHOD_NAMES = {
			"bitPos0b", "bitPos1b", "getBitPos0b", "getBitPos1b", "bitPosition", "getBitPosition"
	};
	private static final String[] BYTE_POSITION_METHOD_NAMES = {
			"bytePos0b", "bytePos1b", "getBytePos0b", "getBytePos1b", "bytePosition", "getBytePosition"
	};
	private static final String[] TYPED_VALUE_METHOD_NAMES = {
			"getObject", "getAnyRef", "getObj"
	};

	private final Map<List<Integer>, ByteRange> rangesByPath = new HashMap<>();
	private final Map<List<Integer>, Object> typedValuesByPath = new HashMap<>();
	private final Deque<Integer> nextChildIndex = new ArrayDeque<>();
	private final Deque<List<Integer>> openElementPaths = new ArrayDeque<>();
	private final Deque<Long> openElementStarts = new ArrayDeque<>();

	/**
	 * The ranges recovered by this parse, keyed by the same child-index path
	 * {@link DomStructureNode} computes for itself from the DOM tree the
	 * other, primary parse produces.
	 */
	Map<List<Integer>, ByteRange> rangesByPath() {
		return rangesByPath;
	}

	/**
	 * The typed simple-element values recovered by this parse - e.g. a real
	 * {@link Integer} for an {@code xs:int} element - keyed the same way as
	 * {@link #rangesByPath()}. Only ever contains entries for simple
	 * (leaf) elements, and only where {@link #tryTypedValueOf} actually
	 * found something.
	 */
	Map<List<Integer>, Object> typedValuesByPath() {
		return typedValuesByPath;
	}

	@Override
	public void reset() {
		rangesByPath.clear();
		typedValuesByPath.clear();
		nextChildIndex.clear();
		openElementPaths.clear();
		openElementStarts.clear();
	}

	@Override
	public void startDocument() {
	}

	@Override
	public void endDocument() {
	}

	@Override
	public void startSimple(InfosetSimpleElement diSimple) {
		enter(diSimple);
	}

	@Override
	public void endSimple(InfosetSimpleElement diSimple) {
		List<Integer> path = exit(diSimple);
		tryTypedValueOf(diSimple).ifPresent(value -> typedValuesByPath.put(path, value));
	}

	@Override
	public void startComplex(InfosetComplexElement diComplex) {
		enter(diComplex);
	}

	@Override
	public void endComplex(InfosetComplexElement diComplex) {
		exit(diComplex);
	}

	@Override
	public void startArray(InfosetArray diArray) {
		// Deliberately no path level and no position capture: DFDL arrays are a
		// grouping concept only here, not a DOM element of their own - see class Javadoc.
	}

	@Override
	public void endArray(InfosetArray diArray) {
	}

	private void enter(Object diElement) {
		List<Integer> myPath;
		if (openElementPaths.isEmpty()) {
			myPath = List.of(); // the document root - matches DomStructureNode's own root path
		} else {
			int myIndex = nextChildIndex.pop();
			nextChildIndex.push(myIndex + 1);
			myPath = new ArrayList<>(openElementPaths.peek());
			myPath.add(myIndex);
		}
		openElementPaths.push(myPath);
		nextChildIndex.push(0); // this element's own children start counting from 0
		openElementStarts.push(positionInBitsOf(diElement).orElse(-1L));
	}

	private List<Integer> exit(Object diElement) {
		nextChildIndex.pop(); // discard this element's own child counter, no longer needed
		List<Integer> myPath = openElementPaths.pop();
		long start = openElementStarts.pop();
		Optional<Long> end = positionInBitsOf(diElement);
		if (start >= 0 && end.isPresent() && end.get() >= start) {
			rangesByPath.put(myPath, new ByteRange(start, end.get() - start));
		}
		return myPath;
	}

	/**
	 * Best-effort bit position of {@code diElement}, tried as a bit offset
	 * first and, only if that fails, as a byte offset converted to bits -
	 * see this class's Javadoc on why both are tried and neither is certain
	 * to exist.
	 */
	private static Optional<Long> positionInBitsOf(Object diElement) {
		Optional<Long> bits = tryMethods(diElement, BIT_POSITION_METHOD_NAMES);
		if (bits.isPresent()) {
			return bits;
		}
		return tryMethods(diElement, BYTE_POSITION_METHOD_NAMES).map(bytePos -> bytePos * 8L);
	}

	private static Optional<Long> tryMethods(Object target, String[] candidateMethodNames) {
		for (String candidate : candidateMethodNames) {
			try {
				Method m = target.getClass().getMethod(candidate);
				Object result = m.invoke(target);
				if (result instanceof Number n) {
					return Optional.of(n.longValue());
				}
			} catch (NoSuchMethodException e) {
				// try the next candidate
			} catch (IllegalAccessException | InvocationTargetException e) {
				// this candidate exists but could not be called - try the next one rather than failing the parse
			}
		}
		return Optional.empty();
	}

	/**
	 * Best-effort: {@code diSimple}'s value as Daffodil's own typed Java
	 * object (a real {@link Integer}, {@link Long}, {@link java.math.BigDecimal},
	 * ... depending on the element's DFDL simple type) rather than DOM text -
	 * see this class's Javadoc. Tries {@link #TYPED_VALUE_METHOD_NAMES} in
	 * order and gives up quietly, the same way {@link #positionInBitsOf}
	 * does, if none exist on this Daffodil version or all return
	 * {@code null}.
	 */
	private static Optional<Object> tryTypedValueOf(Object diSimple) {
		for (String candidate : TYPED_VALUE_METHOD_NAMES) {
			try {
				Method m = diSimple.getClass().getMethod(candidate);
				Object result = m.invoke(diSimple);
				if (result != null) {
					return Optional.of(result);
				}
			} catch (NoSuchMethodException e) {
				// try the next candidate
			} catch (IllegalAccessException | InvocationTargetException e) {
				// this candidate exists but could not be called - try the next one rather than failing the parse
			}
		}
		return Optional.empty();
	}
}
