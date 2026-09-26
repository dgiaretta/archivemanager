package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;
import info.oais.infomodel.interfaces.utility.OaisIfTimeStamp;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class TimeSeriesViewSpecificationReaderTest {

	@Test
	void readsEventStartAndTrailingColumnsFromChildrenRows() {
		StructureNode tree = DefaultStructureNode.builder("series", StructureNodeKind.COMPOSITE)
				.addChild(event("when", 1000L, "label", "start"))
				.addChild(event("when", 2000L, "label", "end"))
				.build();

		TimeSeriesSemanticRepInfo repInfo = new TimeSeriesSemanticRepInfo(
				new TimeSeriesViewSpecification(resource("/timeseries-view/events.xml")));
		OaisIfTimeSeries series = repInfo.apply(tree);

		assertEquals(2, series.getRowCount());
		assertEquals(2, series.getColumnCount());
		assertEquals(OaisIfTimeStamp.class, series.getColumnClass(0));
		assertEquals(1000L, ((OaisIfTimeStamp) series.getValueAt(0, 0)).getTime());
		assertEquals("start", series.getValueAt(0, 1));
		assertEquals(2000L, ((OaisIfTimeStamp) series.getValueAt(1, 0)).getTime());
	}

	@Test
	void readsOptionalEventEndAsColumnOne() {
		StructureNode tree = DefaultStructureNode.builder("series", StructureNodeKind.COMPOSITE)
				.addChild(event("start", 1000L, "end", 5000L))
				.build();

		TimeSeriesSemanticRepInfo repInfo = new TimeSeriesSemanticRepInfo(
				new TimeSeriesViewSpecification(resource("/timeseries-view/with-event-end.xml")));
		OaisIfTimeSeries series = repInfo.apply(tree);

		assertEquals(2, series.getColumnCount());
		assertEquals(OaisIfTimeStamp.class, series.getColumnClass(0));
		assertEquals(OaisIfTimeStamp.class, series.getColumnClass(1));
		assertEquals(1000L, ((OaisIfTimeStamp) series.getValueAt(0, 0)).getTime());
		assertEquals(5000L, ((OaisIfTimeStamp) series.getValueAt(0, 1)).getTime());
	}

	@Test
	void missingEventStartIsRejected() {
		TimeSeriesViewSpecification specification = new TimeSeriesViewSpecification(
				resource("/timeseries-view/missing-event-start.xml"));
		assertThrows(ViewSpecificationException.class, () -> TimeSeriesViewSpecificationReader.read(specification));
	}

	private static StructureNode event(String firstChildName, long firstChildValue, String secondChildName,
			Object secondChildValue) {
		return DefaultStructureNode.builder("event", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf(firstChildName, firstChildValue))
				.addChild(DefaultStructureNode.leaf(secondChildName, secondChildValue))
				.build();
	}

	private static URI resource(String name) {
		try {
			return TimeSeriesViewSpecificationReaderTest.class.getResource(name).toURI();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Malformed resource URI for " + name, e);
		}
	}
}
