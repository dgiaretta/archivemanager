package info.oais.infomodel.implementation;

import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.SemanticRepInfo;

public class SemanticRepInfoRefImpl extends RepresentationInformationRefImpl implements SemanticRepInfo {

	public SemanticRepInfoRefImpl() {
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
	public SemanticRepInfoRefImpl(RepresentationInformation strRi, RepresentationInformation semRi, RepresentationInformation otherRi) {
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
	public SemanticRepInfoRefImpl(DataObject digiObj, RepresentationInformation repInf) {
		super(digiObj, repInf);
		//m_repCat = new RepInfoCategoryRefImpl();
	}

}
