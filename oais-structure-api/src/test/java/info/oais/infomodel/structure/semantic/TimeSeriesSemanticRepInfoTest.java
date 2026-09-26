package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;
import info.oais.infomodel.interfaces.utility.OaisIfTimeStamp;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class TimeSeriesSemanticRepInfoTest {

	@Test
	void columnZeroIsAnOaisIfTimeStamp() {
		StructureNode tree = DefaultStructureNode.builder("events", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("event", StructureNodeKind.COMPOSITE)
						.addChild(DefaultStructureNode.leaf("timestamp", 1_700_000_000_000L))
						.addChild(DefaultStructureNode.leaf("reading", 42))
						.build())
				.addChild(DefaultStructureNode.builder("event", StructureNodeKind.COMPOSITE)
						.addChild(DefaultStructureNode.leaf("timestamp", 1_700_000_060_000L))
						.addChild(DefaultStructureNode.leaf("reading", 43))
						.build())
				.build();

		TimeSeriesMapping mapping = new TimeSeriesMapping(
				root -> root.childrenNamed("event"),
				TimeSeriesMapping.epochMillisColumn("timestamp"),
				null,
				List.of(ColumnMapping.ofChild("reading", Integer.class)));

		OaisIfTimeSeries series = new TimeSeriesSemanticRepInfo(mapping).apply(tree);

		assertEquals(2, series.getRowCount());
		assertEquals(2, series.getColumnCount());
		assertEquals(OaisIfTimeStamp.class, series.getColumnClass(0));

		Object firstTimestamp = series.getValueAt(0, 0);
		assertInstanceOf(OaisIfTimeStamp.class, firstTimestamp);
		assertEquals(1_700_000_000_000L, ((OaisIfTimeStamp) firstTimestamp).getTime());
		assertEquals(43, series.getValueAt(1, 1));
	}
}
