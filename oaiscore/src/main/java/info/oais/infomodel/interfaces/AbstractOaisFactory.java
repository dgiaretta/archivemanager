package info.oais.infomodel.interfaces;


/**
 * Interface for an Abstract Factory used in this OAIS implementation.<br/>
 * For example one could implement this interface to create InformationObjects or InformationPackages.
 * @author David
 * @version 1.0
 * @since 06-Sep-2021 15:59:46
 */
public interface AbstractOaisFactory<T> {

	/**
	 * Create the object requested.
	 *
	 * @param infoType String indicating what type of object to create.
	 * @return The object of type T requested.
	 */
	T create(String infoType);

        /**
	 * Create the object requested.
	 *
	 * @param factoryType String indicating what type of object to create.
	 * @return The object of type T requested.
	 */
	//T createInformationFactory(String infoFactoryType);

        /**
	 * Create the Factory requested.
	 *
	 * @param infoPackageType String indicating what type of object to create.
	 * @return The object of type T requested.
	 */
	//T createPackageFactory(String packageType);

}