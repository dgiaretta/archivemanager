package info.oais.infomodel.structure.drb;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.interfaces.DigitalObject;

/**
 * {@link info.oais.infomodel.structure.ExecutableStructureRepInfo} backed by
 * DRB (CNES/GAEL's Data Request Broker), bridged entirely by reflection - see
 * this module's {@code README-DRB.md}.
 *
 * <p><b>Stream lifetime, unlike the other two adapters:</b> DRB is designed
 * to navigate large, heterogeneous data sources lazily, reading from the
 * underlying stream on demand as the returned node tree is traversed rather
 * than up front. Because of that, this adapter deliberately does
 * <em>not</em> close {@code digitalObject.getObject()}'s stream once
 * {@link #doApply} returns - doing so eagerly, the way the DFDL and Kaitai
 * adapters do (both of which parse fully into memory before returning),
 * would likely break lazy access to a DRB node tree that is still being
 * traversed by the caller. Callers of this adapter are responsible for
 * closing the {@link DigitalObject}'s underlying resource once they are
 * done navigating the result, and should supply a {@link DigitalObject}
 * backed by a repeatable/re-openable stream if {@link #apply} may be called
 * more than once.</p>
 */
public final class DrbStructureRepInfo extends AbstractExecutableStructureRepInfo {

	public DrbStructureRepInfo(DrbFormatSpecification formatSpecification) {
		super(formatSpecification);
	}

	@Override
	public DrbFormatSpecification getFormatSpecification() {
		return (DrbFormatSpecification) super.getFormatSpecification();
	}

	@Override
	protected StructureNode doApply(DigitalObject digitalObject) throws Exception {
		DrbFormatSpecification spec = getFormatSpecification();

		Class<?> resolverClass;
		try {
			resolverClass = Class.forName(spec.getFactoryResolverClassName());
		} catch (ClassNotFoundException e) {
			throw new StructureInterpretationException(
					"DRB class " + spec.getFactoryResolverClassName() + " is not on the classpath - "
							+ "add your DRB/DRB-Cortex jar as a dependency (see README-DRB.md)", e);
		}

		Object resolver = resolveDefaultResolver(resolverClass);

		// Intentionally not try-with-resources: see this class's Javadoc on stream lifetime.
		InputStream in = digitalObject.getObject();
		Object drbNode;
		try {
			Method create = resolver.getClass().getMethod("create", InputStream.class);
			drbNode = create.invoke(resolver, in);
		} catch (NoSuchMethodException e) {
			throw new StructureInterpretationException(
					"DRB factory resolver " + resolver.getClass() + " has no create(InputStream) method - "
							+ "check the API of your installed DRB version and adjust DrbStructureRepInfo accordingly",
					e);
		} catch (InvocationTargetException e) {
			throw new StructureInterpretationException(
					"DRB could not create a node for the supplied DigitalObject using " + spec, e.getCause());
		}

		if (drbNode == null) {
			throw new StructureInterpretationException(
					"DRB returned no node (could not determine a matching format) for " + spec);
		}
		return new DrbStructureNode(drbNode);
	}

	/**
	 * Resolves DRB's singleton default factory resolver. Classic DRB exposes
	 * this as a static {@code getDefaultFactoryResolver()} on
	 * {@code DrbFactoryResolver}; if your version names it differently,
	 * this is the one place to change.
	 */
	private static Object resolveDefaultResolver(Class<?> resolverClass) throws Exception {
		try {
			Method getDefault = resolverClass.getMethod("getDefaultFactoryResolver");
			return getDefault.invoke(null);
		} catch (NoSuchMethodException e) {
			throw new StructureInterpretationException(
					resolverClass + " has no static getDefaultFactoryResolver() method - "
							+ "check the API of your installed DRB version and adjust "
							+ "DrbStructureRepInfo#resolveDefaultResolver accordingly",
					e);
		}
	}
}
