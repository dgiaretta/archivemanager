package info.oais.archive.manager.rdf;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Data saved under the OAIS Information Model's old namespace is moved to the new one. */
class NamespaceRenameTest {

    @Test
    void movesSubjectsPropertiesAndObjectsButNotLiterals() {
        Model m = ModelFactory.createDefaultModel();
        String old = RdfStore.OLD_OAIS_NAMESPACE + "im/";
        String now = RdfStore.NEW_OAIS_NAMESPACE + "im/";
        m.add(m.createResource("http://example.org/archive/do-1"), RDF.type, m.createResource(old + "DigitalObject"));
        m.add(m.createResource("http://example.org/archive/do-1"), m.createProperty(old + "interpretedUsing"),
                m.createResource("http://example.org/archive/ri-1"));
        m.add(m.createResource(old + "DigitalObject"), RDFS.comment, "see " + old);
        m.add(m.createResource("http://example.org/archive/x"), RDFS.label, "unrelated");

        assertThat(RdfStore.renameNamespace(m, RdfStore.OLD_OAIS_NAMESPACE, RdfStore.NEW_OAIS_NAMESPACE)).isEqualTo(3);

        assertThat(m.contains(m.createResource("http://example.org/archive/do-1"), RDF.type,
                m.createResource(now + "DigitalObject"))).isTrue();
        assertThat(m.contains(m.createResource("http://example.org/archive/do-1"), m.createProperty(now + "interpretedUsing"),
                m.createResource("http://example.org/archive/ri-1"))).isTrue();
        assertThat(m.contains(m.createResource(now + "DigitalObject"), RDFS.comment, "see " + old))
                .as("literals are text, left as they are").isTrue();
        assertThat(m.size()).isEqualTo(4);
        assertThat(RdfStore.renameNamespace(m, RdfStore.OLD_OAIS_NAMESPACE, RdfStore.NEW_OAIS_NAMESPACE)).isZero();
    }
}
