package info.oais.infomodel.structure.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.ElementMeaning;
import info.oais.infomodel.structure.manifest.ViewDescription;
import info.oais.infomodel.structure.semantic.ImageSemanticRepInfo;
import info.oais.infomodel.structure.semantic.ImageViewSpecification;
import info.oais.infomodel.structure.semantic.ViewSpecificationException;
import info.oais.infomodel.structure.topcat.RepInfoDecoder;

/**
 * Opens an image through its OAIS Representation Information: decodes the
 * data a manifest describes (see {@link RepInfoDecoder}), and views the tree
 * as rows of pixels with its image view -- an {@code im:ViewSpecification}
 * with {@code im:viewKind "image"}, in oais-structure-api's
 * {@code ImageViewSpecificationReader} format:
 *
 * <pre>{@code
 * <imageView>
 *     <rows select="children" name="row"/>
 *     <pixels select="children" name="pixel"/>
 *     <pixelType type="int" unit="ADU" description="Counts: ..."/>
 * </imageView>
 * }</pre>
 *
 * <p>The pixels' units and description come from {@code <pixelType>}'s
 * optional {@code unit} and {@code description} attributes, else from the
 * manifest's element semantics for the pixel element (matched by structural
 * path, e.g. {@code row.pixel}).</p>
 */
public final class OaisStructureImage {

	private OaisStructureImage() {
	}

	/** Whether {@code data}'s Representation Information gives an image view. */
	public static boolean hasImageView(DescribedData data) {
		return data.view(ViewDescription.IMAGE).isPresent();
	}

	/**
	 * Decodes the image {@code data} describes.
	 *
	 * @throws IOException if it has no image view, or decoding or viewing fails
	 */
	public static DecodedImage open(DescribedData data) throws IOException {
		ViewDescription view = data.view(ViewDescription.IMAGE).orElseThrow(() -> new IOException(
				"The Representation Information gives no image view for " + data.name() + ": it needs an "
						+ "im:ViewSpecification with im:viewKind \"image\" to know how to show the data as rows of pixels"));
		List<Path> temporary = new ArrayList<>();
		try {
			StructureNode tree = RepInfoDecoder.decode(data, temporary);
			Path viewFile = RepInfoDecoder.local(view.location(), ".xml", temporary);
			OaisIfImage image;
			try {
				image = new ImageSemanticRepInfo(new ImageViewSpecification(viewFile.toUri())).apply(tree);
			} catch (ViewSpecificationException | IllegalStateException e) {
				throw new IOException("Failed to view " + data.data() + " as an image: " + e.getMessage(), e);
			}
			Object[][] values = image.getPixelValues();
			if (values.length == 0 || values[0].length == 0) {
				throw new IOException("The image view finds no pixels in " + data.data()
						+ ": check its <rows> and <pixels> name the decoded elements");
			}
			Number[][] pixels = new Number[values.length][];
			for (int r = 0; r < values.length; r++) {
				pixels[r] = new Number[values[r].length];
				for (int c = 0; c < values[r].length; c++) {
					pixels[r][c] = number(values[r][c]);
				}
			}
			Element pixelType = element(viewFile, "pixelType");
			String unit = attribute(pixelType, "unit");
			String description = attribute(pixelType, "description");
			ElementMeaning meaning = data.meaningOf(attribute(element(viewFile, "rows"), "name"),
					attribute(element(viewFile, "pixels"), "name")).orElse(null);
			if (meaning != null) {
				unit = unit != null ? unit : meaning.units();
				description = description != null ? description : describe(meaning);
			}
			List<String> history = new ArrayList<>();
			history.add("Decoded from " + data.data());
			history.add("through the OAIS Representation Information of " + data.iri());
			try {
				return new DecodedImage(data.name(), pixels, image.getPixelClass(), unit, description, history);
			} catch (IllegalArgumentException e) {
				throw new IOException(e.getMessage(), e);
			}
		} finally {
			RepInfoDecoder.deleteAll(temporary);
		}
	}

	/** A pixel's decoded value as a number; null if it has none. */
	static Number number(Object value) {
		if (value instanceof Number n) {
			return n;
		}
		if (value instanceof Boolean b) {
			return b ? 1 : 0;
		}
		if (value instanceof CharSequence s && !s.toString().isBlank()) {
			String text = s.toString().strip();
			try {
				return Long.parseLong(text);
			} catch (NumberFormatException e) {
				try {
					return Double.parseDouble(text);
				} catch (NumberFormatException e2) {
					return null;
				}
			}
		}
		return null;
	}

	private static String describe(ElementMeaning m) {
		if (m.label() != null && m.definition() != null) {
			return m.label() + ": " + m.definition();
		}
		return m.label() != null ? m.label() : m.definition();
	}

	private static Element element(Path view, String name) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			return (Element) factory.newDocumentBuilder().parse(view.toFile()).getElementsByTagName(name).item(0);
		} catch (Exception e) {
			return null;
		}
	}

	private static String attribute(Element e, String name) {
		String value = e == null ? "" : e.getAttribute(name).strip();
		return value.isEmpty() ? null : value;
	}
}
