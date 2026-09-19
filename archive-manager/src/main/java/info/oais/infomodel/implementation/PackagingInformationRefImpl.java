/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import info.oais.infomodel.interfaces.PackagingInformation;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.representationinformation.RepInfoCategory;

/**
 *
 */
public class PackagingInformationRefImpl extends RepresentationInformationRefImpl implements PackagingInformation {

	RepresentationInformation m_RIO = null;

	/**
	 * Constructor for PackagingInformation as an Information Package
	 */
	public PackagingInformationRefImpl() {
		super();
	}
	/**
	 * Set the RepresentationInformation Object for the PackagingInformation.
	 *
	 * @param rio Set the Information Object for the FReferenceInformation.
	 */
	public PackagingInformationRefImpl(RepresentationInformation rio) {
		super(rio.getDataObject(), rio.getRepresentationInformation());
		m_RIO = rio;
	}
	@Override
	@JsonIgnore
	public String getString() {
		return getDataObject().toString();
	}

	public void setString(String desc) {
		this.setDataObject(new DataObjectRefImpl(desc));

	}
	@Override
	public RepInfoCategory getCategory() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void setCategory(RepInfoCategory cat) {
		// TODO Auto-generated method stub

	}
	@Override
	public RepresentationInformation getSemanticRepInfo() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RepresentationInformation getStructureRepInfo() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public RepresentationInformation getOtherRepInfo() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void putSemanticRepInfo(RepresentationInformation semRI) {
		// TODO Auto-generated method stub

	}
	@Override
	public void putStructureRepInfo(RepresentationInformation strRI) {
		// TODO Auto-generated method stub

	}
	@Override
	public void putOtherRepInfo(RepresentationInformation otherRI) {
		// TODO Auto-generated method stub

	}

}
