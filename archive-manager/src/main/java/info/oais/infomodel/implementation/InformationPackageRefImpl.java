/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.UUID;

import info.oais.infomodel.interfaces.IdentifierObject;
import info.oais.infomodel.interfaces.InformationObject;
import info.oais.infomodel.interfaces.InformationPackage;
import info.oais.infomodel.interfaces.PackageDescription;
import info.oais.infomodel.interfaces.PackagingInformation;
import info.oais.infomodel.interfaces.PreservationDescriptionInformation;




/**
 * Implementation of an Information Package.
 * This has from the OAIS Information Model
 * <ul>
 * <li>InformationObject</li>
 * <li>PackageDescription</li>
 * <li>PackagingInformation</li>
 * </ul>
 * plus additional aspects:
 * <ul>
 * <li>Version - which applies to this implementation</li>
 * <li>PackageType - e.g. General or AIP</li>
 * </ul>
 * @author david
 *
 */

@JsonRootName(value = "InformationPackage" )
//@JsonPropertyOrder({"InformationPackage", "PackageType", "Version", "IsComplete", "PackageType", "PackageDescription", "PackagingInformation","InformationObject","PreservationDescriptionInformation"  })
@JsonPropertyOrder({"InformationPackage", "PackageVersion", "PackageType", "PackageDescription", "PackagingInformation","InformationObject", "PreservationDescriptionInformation", "IsDeclaredComplete", "version"  })
public class InformationPackageRefImpl extends DigitalObjectRefImpl implements InformationPackage {


	public static String VERSION = "1.0.0";
	String m_version = VERSION;
	String m_PackageType = "General";
	PackageDescription m_PackageDescription = null;
	PackagingInformation m_PI = null;
	InformationObject m_IO = null;
	PackageDescription m_PD = null;
	IdentifierObject m_ID = null;
	PreservationDescriptionInformation m_PreservationDescriptionInformation = null;
	ObjVersionRefImpl m_ObjVersion = new ObjVersionRefImpl();
	boolean m_IsDeclaredComplete = false;
	
	/**
	 * Create new InfoPackage
	 *
	 *
	 * @param io 			InformationObject for the IP
	 * @param pd 			Package Description
	 * @param packageType 	Package type e.g. "General", "AIP", Query", "Response" etc TODO - ENUM
	 * @param id			IdentifierObject for the IP
	 *
	 */
	public InformationPackageRefImpl(InformationObject io, PackageDescription pd, String packageType, IdentifierObject id, PreservationDescriptionInformation pdi, boolean isComplete) {
		super();
		m_IO = io;
		m_PackageType = packageType;
		m_PD = pd;
		m_PackageDescription = pd;
		m_PreservationDescriptionInformation = new PreservationDescriptionInformationRefImpl();
		m_IsDeclaredComplete = isComplete;
		/**
		 * Generate an initial UUID  if the id supplied is null
		 */
		if (id == null) {
			m_ID = new IdentifierObjectRefImpl((UUID.randomUUID()).toString(), "UUID");
		} else {
			m_ID = id;
		}
	}


	public InformationPackageRefImpl() {
		super();
		// TODO Auto-generated constructor stub
	}


	/**
	 * Get String showing this is an IP
	 *
	 * @return String showing this is an IP
	 */
	@JsonGetter("PackageType")
	public String getPackageType() {
		return m_PackageType;
	}
	/**
	 * Set String stating this is an AIP
	 *
	 * @param pt  String for PackageType
	 */
	@JsonSetter("PackageType")
	public void setPackageType(String pt) {
		m_PackageType = pt;
	}

	/**
	 * Get Content Information, which is an Information Object, of AIP
	 *
	 * @return Content Information, which is an Information Object, of AIP
	 */
	@JsonGetter("InformationObject")
	public InformationObject getInformationObject() {
		return m_IO;
	}
	@JsonSetter("InformationObject")
	public void setInformationObject(InformationObject infoObj) {
		m_IO = infoObj;
	}

	@JsonGetter("PackageDescription")
	public PackageDescription getPackageDescription() {
		return m_PackageDescription;
	}
	@JsonSetter("PackageDescription")
	public void setPackageDescription(PackageDescription pd) {
		m_PackageDescription = pd;

	}
	@JsonGetter("PackagingInformation")
	public PackagingInformation getPackagingInformation() {
		return m_PI;
	}
	@JsonSetter("PackagingInformation")
	public void setPackagingInformation(PackagingInformation pi) {
		m_PI =  pi;

	}
	@JsonGetter("PackageVersion")
	public String getPackageVersion() {
		return m_version;
	}
	@JsonSetter("PackageVersion")
	public void setPackageVersion(String ver) {
		m_version =  ver;

	}


	@Override
	public PreservationDescriptionInformation getPDI() {
		// TODO Auto-generated method stub
		return m_PreservationDescriptionInformation;
	}


	@Override
	public void setPDI(PreservationDescriptionInformation pdiObj) {
		// TODO Auto-generated method stub
		m_PreservationDescriptionInformation = pdiObj;
	}


	@Override
	public boolean getIsDeclaredComplete() {
		// TODO Auto-generated method stub
		return m_IsDeclaredComplete;
	}


	@Override
	public void setIsDeclaredComplete(boolean idc) {
		// TODO Auto-generated method stub
		m_IsDeclaredComplete = idc;
	}

}
