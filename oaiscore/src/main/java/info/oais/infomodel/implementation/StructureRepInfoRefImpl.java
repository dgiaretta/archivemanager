/**
 *
 */
package info.oais.infomodel.implementation;

import info.oais.infomodel.interfaces.DataObject;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.StructureRepInfo;

/**
 *
 */
public class StructureRepInfoRefImpl extends RepresentationInformationRefImpl implements StructureRepInfo {

	public StructureRepInfoRefImpl() {
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
	public StructureRepInfoRefImpl(RepresentationInformation strRi, RepresentationInformation semRi, RepresentationInformation otherRi) {
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
	public StructureRepInfoRefImpl(DataObject digiObj, RepresentationInformation repInf) {
		super(digiObj, repInf);
		//m_repCat = new RepInfoCategoryRefImpl();
	}

}
