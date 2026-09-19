/**
 *
 */
package info.oais.infomodel.implementation.representationinformation;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import info.oais.infomodel.interfaces.representationinformation.RepInfoCategory;

/**
 * @author david
 *
 */
public class RepInfoCategoryRefImpl implements RepInfoCategory {
	String m_CAT = null;
	/**
	 *
	 */
	public RepInfoCategoryRefImpl() {
		super();
	}

	/**
	 *
	 */
	public RepInfoCategoryRefImpl(String cat) {
		super();
		m_CAT = cat;
	}

	@JsonGetter("RepInfoCategory")
	public String getCategory() {
		return m_CAT;
	}

	@JsonSetter("RepInfoCategory")
	public void setCategory(String cat) {
		m_CAT = cat;
	}

}
