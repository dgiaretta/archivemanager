/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import info.oais.infomodel.interfaces.AccessRightsInformation;
import info.oais.infomodel.interfaces.ContextInformation;
import info.oais.infomodel.interfaces.FixityInformation;
import info.oais.infomodel.interfaces.PreservationDescriptionInformation;
import info.oais.infomodel.interfaces.ProvenanceInformation;
import info.oais.infomodel.interfaces.ReferenceInformation;

/**
 * @author david
 *
 */
public class PreservationDescriptionInformationRefImpl extends InformationObjectRefImpl
		implements PreservationDescriptionInformation {
	AccessRightsInformation m_ARI = null;
	ReferenceInformation m_REF = null;
	ProvenanceInformation m_PROV = null;
	ContextInformation m_CONT = null;
	FixityInformationRefImpl m_FIX = null;

	public PreservationDescriptionInformationRefImpl(ProvenanceInformation prov, ReferenceInformation ref, AccessRightsInformation ar,
			ContextInformation con, FixityInformation fi) {
		super();
		m_PROV = prov;
		m_REF = ref;
		m_ARI = ar;
		m_CONT = con;
		m_FIX = (FixityInformationRefImpl)fi;
	}


	public PreservationDescriptionInformationRefImpl() {
		//TODO Auto-generated constructor stub
	}


	@JsonGetter("AccessRights")
	public AccessRightsInformation getAccessRightsInformation() {
		return m_ARI;
	}
	@JsonSetter("AccessRights")
	public void setAccessRightsInformation(AccessRightsInformation ari) {
		m_ARI = ari;
	}
	@JsonGetter("Context")
	public ContextInformation getContextInformation() {
		return m_CONT;
	}
	@JsonSetter("Context")
	public void setContextInformation(ContextInformation ci) {
		m_CONT = ci;
	}
	@JsonGetter("Fixity")
	public FixityInformationRefImpl getFixityInformation() {
		return m_FIX;
	}

	@JsonSetter("Fixity")
	public void setFixityInformation(FixityInformation fi) {
		m_FIX = (FixityInformationRefImpl)fi;
	}
	@JsonGetter("Provenance")
	public ProvenanceInformation getProvenanceInformation() {
		return m_PROV;
	}

	@JsonSetter("Provenance")
	public void setProvenanceInformation(ProvenanceInformation pi) {
		m_PROV = pi;
	}
	@JsonGetter("Reference")
	public ReferenceInformation getReferenceInformation() {
		return m_REF;
	}

	@JsonSetter("Reference")
	public void setReferenceInformation(ReferenceInformation ri) {
		m_REF = ri;
	}


}
