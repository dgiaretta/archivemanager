package info.oais.infomodel.structure.dfdl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.daffodil.japi.Compiler;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.infoset.W3CDOMInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.interfaces.DigitalObject;

/**
 * {@link info.oais.infomodel.structure.ExecutableStructureRepInfo} backed by
 * Apache Daffodil, the Apache-hosted reference implementation of the OGF
 * Data Format Description Language (DFDL).
 *
 * <p>The DFDL schema given by the {@link DfdlFormatSpecification} is
 * compiled once (on first {@link #apply}) and the resulting
 * {@link DataProcessor} is cached for reuse - schema compilation is the
 * expensive part of using Daffodil, parsing an individual Digital Object
 * against an already-compiled schema is comparatively cheap.</p>
 *
 * <p><b>Root element:</b> this adapter always parses against the schema's
 * default root (whatever {@code ProcessorFactory#onPath("/")} resolves to).
 * Selecting a specific top-level element among several defined in one
 * schema is supported by Daffodil, but the exact API for it has moved
 * between major Daffodil versions; rather than guess at an overload that
 * might not exist in the version you have, this class deliberately leaves
 * that as a documented extension point - see {@link #compile()}.</p>
 *
 * <p>The parsed result is obtained via Daffodil's
 * {@link W3CDOMInfosetOutputter}, i.e. as a standard {@link Document}, and
 * wrapped element-by-element by {@link DomStructureNode}; nothing is copied
 * out of the DOM tree, so it is retained for the life of the returned
 * {@link StructureNode}.</p>
 *
 * <p><b>Bit-level provenance:</b> each {@link #doApply} additionally runs a
 * second, best-effort parse of the same bytes through
 * {@link PositionTrackingInfosetOutputter} to recover per-element byte
 * ranges for {@link StructureNode#getSourceRange()} - see
 * {@link #tryCaptureExtras} and that class's Javadoc for the full
 * rationale and caveats - the same second parse also recovers each simple
 * element's already-typed value for {@link DomStructureNode#getValue()}, see
 * {@link PositionTrackingInfosetOutputter}'s Javadoc on "Typed values". This
 * is why {@code digitalObject}'s bytes are now
 * read fully into memory up front rather than streamed directly into the
 * (single) parse the way earlier versions of this class did: parsing twice
 * needs two independent, rewindable views of the same bytes. For very large
 * Digital Objects where that extra buffering/parsing cost matters more than
 * having source ranges, the straightforward fix is to make the second parse
 * conditional (a flag on {@link DfdlFormatSpecification}, say) - left out
 * here to keep this adapter's first cut at source ranges simple.</p>
 */
public final class DfdlStructureRepInfo extends AbstractExecutableStructureRepInfo {

	private volatile DataProcessor dataProcessor;

	public DfdlStructureRepInfo(DfdlFormatSpecification formatSpecification) {
		super(formatSpecification);
	}

	@Override
	public DfdlFormatSpecification getFormatSpecification() {
		return (DfdlFormatSpecification) super.getFormatSpecification();
	}

	@Override
	protected StructureNode doApply(DigitalObject digitalObject) throws Exception {
		DataProcessor processor = dataProcessor();

		byte[] bytes;
		try (InputStream in = digitalObject.getObject()) {
			bytes = in.readAllBytes();
		}

		ParseExtras extras = tryCaptureExtras(processor, bytes);

		InputSourceDataInputStream input = new InputSourceDataInputStream(new ByteArrayInputStream(bytes));
		W3CDOMInfosetOutputter outputter = new W3CDOMInfosetOutputter();

		ParseResult result = processor.parse(input, outputter);
		if (result.isError()) {
			throw new StructureInterpretationException(
					describeDiagnostics("DFDL parse failed against " + getFormatSpecification(),
							result.getDiagnostics()));
		}

		Document document = outputter.getResult();
		Element root = document.getDocumentElement();
		if (root == null) {
			throw new StructureInterpretationException(
					"DFDL parse produced no root element for " + getFormatSpecification());
		}
		return new DomStructureNode(root, extras.rangesByPath(), extras.typedValuesByPath(), List.of());
	}

	/**
	 * The two per-element maps {@link #tryCaptureExtras} recovers from its
	 * second, best-effort parse - see that method and
	 * {@link PositionTrackingInfosetOutputter}'s Javadoc.
	 */
	private record ParseExtras(Map<List<Integer>, ByteRange> rangesByPath, Map<List<Integer>, Object> typedValuesByPath) {

		private static final ParseExtras EMPTY = new ParseExtras(Map.of(), Map.of());
	}

	/**
	 * Best-effort: re-parses {@code bytes} a second time purely to recover
	 * per-element bit positions and typed simple-element values via
	 * {@link PositionTrackingInfosetOutputter}. Never throws: any failure - a
	 * parse error specific to the position-tracking outputter, or this
	 * Daffodil version's infoset element classes not matching what that
	 * class guesses at - simply means no source ranges or typed values are
	 * available, and the real parse in {@link #doApply} proceeds, unaffected,
	 * either way.
	 */
	private ParseExtras tryCaptureExtras(DataProcessor processor, byte[] bytes) {
		try {
			PositionTrackingInfosetOutputter tracker = new PositionTrackingInfosetOutputter();
			InputSourceDataInputStream input = new InputSourceDataInputStream(new ByteArrayInputStream(bytes));
			ParseResult result = processor.parse(input, tracker);
			if (result.isError()) {
				return ParseExtras.EMPTY;
			}
			return new ParseExtras(tracker.rangesByPath(), tracker.typedValuesByPath());
		} catch (RuntimeException | LinkageError e) {
			return ParseExtras.EMPTY;
		}
	}

	/**
	 * Compiles {@link #getFormatSpecification()}'s schema, once, the first
	 * time this instance is applied. Thread-safe (double-checked locking):
	 * concurrent callers block on the first compilation rather than each
	 * triggering their own.
	 */
	private DataProcessor dataProcessor() {
		DataProcessor result = dataProcessor;
		if (result == null) {
			synchronized (this) {
				result = dataProcessor;
				if (result == null) {
					result = compile();
					dataProcessor = result;
				}
			}
		}
		return result;
	}

	private DataProcessor compile() {
		DfdlFormatSpecification spec = getFormatSpecification();

		Compiler compiler = Daffodil.compiler();
		ProcessorFactory processorFactory;
		try {
			processorFactory = compiler.compileSource(spec.getSchemaLocation());
		} catch (IOException e) {
			throw new StructureInterpretationException(
					"Unable to read DFDL schema " + spec.getSchemaLocation(), e);
		}
		if (processorFactory.isError()) {
			throw new StructureInterpretationException(
					describeDiagnostics("Unable to compile DFDL schema " + spec.getSchemaLocation(),
							processorFactory.getDiagnostics()));
		}

		DataProcessor processor = processorFactory.onPath("/");
		if (processor.isError()) {
			throw new StructureInterpretationException(
					describeDiagnostics("Unable to create a DFDL data processor for " + spec.getSchemaLocation(),
							processor.getDiagnostics()));
		}
		return processor;
	}

	private static String describeDiagnostics(String message, List<Diagnostic> diagnostics) {
		String details = diagnostics.stream()
				.map(Diagnostic::getMessage)
				.collect(Collectors.joining(System.lineSeparator() + "  - ",
						diagnostics.isEmpty() ? "" : System.lineSeparator() + "  - ", ""));
		return message + details;
	}
}
