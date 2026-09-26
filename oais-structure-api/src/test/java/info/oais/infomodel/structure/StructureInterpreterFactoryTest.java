package info.oais.infomodel.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;

class StructureInterpreterFactoryTest {

	@Test
	void dispatchesToTheRegisteredProviderForTheSpecificationsLanguage() {
		StructureInterpreterFactory factory = new StructureInterpreterFactory(List.of(new FakeProvider()));

		assertEquals(Set.of(SpecificationLanguage.OTHER), factory.availableLanguages());

		FormatSpecification spec = () -> SpecificationLanguage.OTHER;
		ExecutableStructureRepInfo repInfo = factory.create(spec);

		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream("ignored".getBytes()));
		StructureNode result = repInfo.apply(digitalObject);

		assertEquals("fake", result.getName());
	}

	@Test
	void createByNameIsUnsupportedBecauseAFullSpecificationIsRequired() {
		StructureInterpreterFactory factory = new StructureInterpreterFactory(List.of(new FakeProvider()));
		assertThrows(UnsupportedOperationException.class, () -> factory.create("OTHER"));
	}

	@Test
	void failsClearlyWhenNoProviderIsRegisteredForTheLanguage() {
		StructureInterpreterFactory factory = new StructureInterpreterFactory(List.of());
		FormatSpecification spec = () -> SpecificationLanguage.DFDL;

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> factory.create(spec));
		assertTrue(ex.getMessage().contains("oais-structure-dfdl"));
	}

	@Test
	void unavailableProvidersAreNotRegistered() {
		StructureInterpreterFactory factory = new StructureInterpreterFactory(List.of(new FakeProvider(false)));
		assertTrue(factory.availableLanguages().isEmpty());
	}

	private static final class FakeProvider implements StructureInterpreterProvider {
		private final boolean available;

		FakeProvider() {
			this(true);
		}

		FakeProvider(boolean available) {
			this.available = available;
		}

		@Override public SpecificationLanguage getSpecificationLanguage() { return SpecificationLanguage.OTHER; }
		@Override public boolean isAvailable() { return available; }
		@Override public ExecutableStructureRepInfo create(FormatSpecification spec) {
			return new AbstractExecutableStructureRepInfo(spec) {
				@Override protected StructureNode doApply(DigitalObject digitalObject) {
					return DefaultStructureNode.leaf("fake", "value");
				}
			};
		}
	}
}
