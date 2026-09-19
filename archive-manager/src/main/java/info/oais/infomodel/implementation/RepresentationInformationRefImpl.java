/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoCategory;

/**
 * @author david
 *
 */
public class RepresentationInformationRefImpl extends InformationObjectRefImpl implements RepresentationInformation {
	@JsonIgnore
	RepInfoCategory m_repCat = null;
	@JsonIgnore
	public RepresentationInformation m_OtherRepInfo;
	@JsonIgnore
	public RepresentationInformation m_SemanticRepInfo;
	@JsonIgnore
	public RepresentationInformation m_StructureRepInfo;

	/**
	 * Create RepInfo
	 */
	public RepresentationInformationRefImpl() {
		super();
		//m_repCat = new RepInfoCategoryRefImpl();
	}
	/**
	 * Create RepInfo with all components
	 *
	 * @param strRi Structure RI
	 * @param semRi Semantic RI
	 * @param otherRi Other RI
	 */
	public RepresentationInformationRefImpl(RepresentationInformation strRi, RepresentationInformation semRi, RepresentationInformation otherRi) {
		//super();
		m_StructureRepInfo = strRi;
		m_SemanticRepInfo = semRi;
		m_OtherRepInfo = otherRi;

		//m_repCat = new RepInfoCategoryRefImpl();
	}
	/**
	 * Create RepInfo with specific Data and RepInfo objects
	 *
	 * @param digiObj Data Object for this Information Object
	 * @param repInf  The Data Object of the Representation Information for this Information Object
	 */
	public RepresentationInformationRefImpl(DataObject digiObj, RepresentationInformation repInf) {
		super(digiObj, repInf);
		//m_repCat = new RepInfoCategoryRefImpl();
	}

	//@JsonGetter("RICategory")
	public RepInfoCategory getCategory() {
		return m_repCat;
	}

	//@JsonSetter("RICategory")
	public void setCategory(RepInfoCategory cat) {
		m_repCat = cat;

	}
	@Override
	public RepresentationInformation getSemanticRepInfo() {

		return m_SemanticRepInfo;
	}
	@Override
	public RepresentationInformation getStructureRepInfo() {

		return m_StructureRepInfo;
	}
	@Override
	public RepresentationInformation getOtherRepInfo() {

		return m_OtherRepInfo;
	}
	@Override
	public void putSemanticRepInfo(RepresentationInformation semRI) {
		m_SemanticRepInfo = semRI;

	}
	@Override
	public void putStructureRepInfo(RepresentationInformation strRI) {
		m_StructureRepInfo = strRI;

	}
	@Override
	public void putOtherRepInfo(RepresentationInformation otherRI) {
		m_OtherRepInfo = otherRI;

	}

}
