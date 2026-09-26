package info.oais.infomodel.structure.drb.fakedrb;

import java.util.ArrayList;
import java.util.List;

/**
 * See {@link FakeDrbFactoryResolver}. Deliberately named and shaped like a
 * minimal {@code fr.gael.drb.DrbNode}: {@code getName()}, {@code getValue()},
 * {@code getChildrenList()}.
 */
public final class FakeDrbNode {

	private final String name;
	private final String value;
	private final List<FakeDrbNode> children = new ArrayList<>();

	FakeDrbNode(String name, String value) {
		this.name = name;
		this.value = value;
	}

	void addChild(FakeDrbNode child) {
		children.add(child);
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}

	public List<FakeDrbNode> getChildrenList() {
		return children;
	}
}
