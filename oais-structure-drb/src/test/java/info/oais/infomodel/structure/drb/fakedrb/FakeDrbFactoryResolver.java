package info.oais.infomodel.structure.drb.fakedrb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A minimal stand-in for {@code fr.gael.drb.DrbFactoryResolver}, used only
 * by this module's tests to exercise {@code DrbStructureRepInfo} and
 * {@code DrbStructureNode} without a real DRB jar on the test classpath.
 *
 * <p>It implements exactly the two methods {@code DrbStructureRepInfo} looks
 * for by default ({@code getDefaultFactoryResolver()} and
 * {@code create(InputStream)}) and produces {@link FakeDrbNode}s, which in
 * turn implement exactly the node methods {@code DrbStructureNode} looks
 * for. If the real DRB's method names turn out to differ, only this test
 * fake and the candidate name lists in the production classes need to
 * change - which is exactly the point of bridging via reflection.</p>
 *
 * <p>The tiny "protocol" understood here: the stream must contain
 * {@code "x=<int>;y=<int>;label=<text>"}, which is parsed into a
 * three-child fake node tree.</p>
 */
public final class FakeDrbFactoryResolver {

	private static final FakeDrbFactoryResolver INSTANCE = new FakeDrbFactoryResolver();

	public static FakeDrbFactoryResolver getDefaultFactoryResolver() {
		return INSTANCE;
	}

	public FakeDrbNode create(InputStream in) throws IOException {
		String text = readAll(in);
		FakeDrbNode root = new FakeDrbNode("point", null);
		for (String part : text.split(";")) {
			String[] kv = part.split("=", 2);
			root.addChild(new FakeDrbNode(kv[0], kv.length > 1 ? kv[1] : null));
		}
		return root;
	}

	private static String readAll(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		in.transferTo(buffer);
		return buffer.toString(StandardCharsets.UTF_8);
	}
}
