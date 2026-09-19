/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import info.oais.infomodel.interfaces.PackageDescription;

/**
 * @author david
 *
 */
public class PackageDescriptionRefImpl extends InformationObjectRefImpl implements PackageDescription {

	/**
	 * Create Package Description as String - assumed to be English encoded as UTF-16
	 * @param pdStr The String to assign to the Package Description, assumed to be English encoded as UTF-16
	 */
	public PackageDescriptionRefImpl(String pdStr) {
		super();
		this.setDataObject(new DataObjectRefImpl(pdStr));
		String repInfo = "English encoded in UTF-16";
		this.setRepresentationInformation(new RepresentationInformationRefImpl(new DataObjectRefImpl(repInfo), null));
	}
	@Override
	@JsonIgnore
	public String getString() {
		return getDataObject().toString();
	}

	public void setString(String desc) {
		this.setDataObject(new DataObjectRefImpl(desc));
	}

}
