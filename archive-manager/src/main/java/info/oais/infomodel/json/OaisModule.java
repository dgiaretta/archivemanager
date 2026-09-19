package info.oais.infomodel.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;

public final class OaisModule {

    private OaisModule() {}

    public static Module create() {
        SimpleModule module = new SimpleModule();

        module.registerSubtypes(
        	info.oais.infomodel.implementation.InformationObjectRefImpl.class,
        	info.oais.infomodel.implementation.DigitalObjectRefImpl.class,
            info.oais.infomodel.implementation.RepresentationInformationRefImpl.class,
            info.oais.infomodel.implementation.AccessRightsInformationRefImpl.class,
            info.oais.infomodel.implementation.ArchivalInformationPackageRefImpl.class,
            info.oais.infomodel.implementation.ContextInformationRefImpl.class,
            info.oais.infomodel.implementation.FixityInformationRefImpl.class,
            info.oais.infomodel.implementation.IdentifierObjectRefImpl.class,
            info.oais.infomodel.implementation.InformationPackageRefImpl.class,
            info.oais.infomodel.implementation.ObjVersionRefImpl.class,
            info.oais.infomodel.implementation.OtherRepInfoRefImpl.class,
            info.oais.infomodel.implementation.PackageDescriptionRefImpl.class,
            info.oais.infomodel.implementation.PackagingInformationRefImpl.class,
            info.oais.infomodel.implementation.ProvenanceInformationRefImpl.class,
            info.oais.infomodel.implementation.ReferenceInformationRefImpl.class,
            info.oais.infomodel.implementation.RepresentationInformationNetworkRefImpl.class,
            info.oais.infomodel.implementation.SemanticRepInfoRefImpl.class,
            info.oais.infomodel.implementation.StructureRepInfoRefImpl.class
        );

        return module;
    }
}