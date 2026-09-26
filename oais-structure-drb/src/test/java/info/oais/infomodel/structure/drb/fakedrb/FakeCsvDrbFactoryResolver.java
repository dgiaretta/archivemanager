package info.oais.infomodel.structure.drb.fakedrb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Another minimal stand-in for {@code fr.gael.drb.DrbFactoryResolver} - see
 * {@link FakeDrbFactoryResolver}'s Javadoc for why a fake rather than a real
 * DRB jar - this one for a variable number of repeated, comma-delimited
 * {@code x,y,label} text records rather than {@link FakeDrbFactoryResolver}'s
 * single {@code x=...;y=...;label=...} record.
 *
 * <p>Modelled the way a real DRB CSV driver (e.g. GAEL's {@code drb-csv}
 * addon) exposes a delimited file - and the way DFDL's
 * {@code csv-points.dfdl.xsd} does too - as repeated same-named
 * {@code row} children under one root node, <em>not</em> DRB Java's own
 * array/list type: see {@link info.oais.infomodel.structure.StructureNodeKind}'s
 * Javadoc on ARRAY vs. repeated COMPOSITE siblings, and {@code DrbStructureNode}'s
 * Javadoc on why any node with children is COMPOSITE. This is what lets
 * {@code oais-structure-demo}'s {@code points-table-view.xml} (written once,
 * against {@code select="children" name="row"}) apply unchanged to both this
 * fake DRB tree and a real {@code csv-points.dfdl.xsd} parse - the same
 * interoperability payoff {@code point-table-view.xml} demonstrates between
 * Kaitai Struct and DFDL for a single record.</p>
 */
public final class FakeCsvDrbFactoryResolver {

	private static final FakeCsvDrbFactoryResolver INSTANCE = new FakeCsvDrbFactoryResolver();

	public static FakeCsvDrbFactoryResolver getDefaultFactoryResolver() {
		return INSTANCE;
	}

	public FakeDrbNode create(InputStream in) throws IOException {
		String text = readAll(in);
		FakeDrbNode root = new FakeDrbNode("rows", null);
		for (String line : text.split("\n")) {
			if (line.isBlank()) {
				continue;
			}
			String[] fields = line.split(",", 3);
			FakeDrbNode row = new FakeDrbNode("row", null);
			row.addChild(new FakeDrbNode("x", fields[0]));
			row.addChild(new FakeDrbNode("y", fields[1]));
			row.addChild(new FakeDrbNode("label", fields[2]));
			root.addChild(row);
		}
		return root;
	}

	private static String readAll(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		in.transferTo(buffer);
		return buffer.toString(StandardCharsets.UTF_8);
	}
}
