/**
 *
 */
package info.oais.infomodel.implementation;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import info.oais.infomodel.interfaces.InformationObject;
import info.oais.infomodel.interfaces.ReferenceInformation;

/**
 * @author david
 *
 */
public class ReferenceInformationRefImpl extends InformationObjectRefImpl implements ReferenceInformation {

	InformationObject m_IO = null;

	/**
	 * Constructor for FixityInformation as an Information Object
	 */
	public ReferenceInformationRefImpl() {
		super();
	}
	/**
	 * Set the Information Object for the ReferenceInformation.
	 *
	 * @param io Set the Information Object for the FReferenceInformation.
	 */
	public ReferenceInformationRefImpl(InformationObject io) {
		super(io.getDataObject(), io.getRepresentationInformation());
		m_IO = io;
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
