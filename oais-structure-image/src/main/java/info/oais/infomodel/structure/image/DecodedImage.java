package info.oais.infomodel.structure.image;

import java.util.List;

/**
 * An image decoded through its Representation Information: its pixel values
 * in the order the data holds them (row 0 first), and what they mean.
 *
 * @param name        what the Data Object is called
 * @param pixels      the pixel values, {@code pixels[row][column]}; a missing value is null
 * @param pixelClass  the pixels' declared class, from the image view ({@code <pixelType>})
 * @param unit        the pixels' units, or null
 * @param description what a pixel value means, or null
 * @param history     how it was obtained (the data's location, the description used), for the record
 */
public record DecodedImage(String name, Number[][] pixels, Class<?> pixelClass, String unit, String description,
		List<String> history) {

	public DecodedImage {
		if (pixels.length == 0 || pixels[0].length == 0) {
			throw new IllegalArgumentException(name + " has no pixels");
		}
		for (int r = 1; r < pixels.length; r++) {
			if (pixels[r].length != pixels[0].length) {
				throw new IllegalArgumentException(name + "'s rows aren't all the same width: row " + r + " has "
						+ pixels[r].length + " pixels, row 0 has " + pixels[0].length);
			}
		}
		history = List.copyOf(history);
	}

	public int width() {
		return pixels[0].length;
	}

	public int height() {
		return pixels.length;
	}
}
