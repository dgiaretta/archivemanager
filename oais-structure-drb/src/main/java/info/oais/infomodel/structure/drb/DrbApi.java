package info.oais.infomodel.structure.drb;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * The one place this module touches GAEL's Java DRB (verified against DRB
 * 2.5.13, LGPL v3 - see {@code third-party/README.md}), by reflection so the module still
 * compiles, and {@link DrbStructureInterpreterProvider#isAvailable()} simply
 * reports {@code false}, without the jar. Every call goes through DRB's
 * public interfaces ({@code fr.gael.drb.DrbNode}, {@code DrbFactoryImpl},
 * {@code DrbAttributeList}, ...) rather than the concrete classes behind
 * them, some of which are not public.
 *
 * <p>DRB documents no thread-safety guarantee, so calls into it are
 * serialised on one lock.</p>
 */
final class DrbApi {

	/** Upper bound on the nodes copied out of one DRB tree. */
	static final int MAX_NODES = 1_000_000;

	private static final Object LOCK = new Object();

	/** SDF attributes asked for by name when a node lists none (see {@link #attributes}). */
	private static final String[] NAMED_ATTRIBUTES = {"offset", "length", "documentation"};

	private DrbApi() {
	}

	static boolean isAvailable() {
		try {
			Class.forName("fr.gael.drb.DrbFactory");
			Class.forName("fr.gael.drb.impl.sds.SdfFactory");
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/**
	 * @param data   the data file to interpret
	 * @param schema a DRB SDF schema to apply, or {@code null} to let DRB recognise the format itself
	 * @return the decoded tree, copied out of DRB
	 */
	static StructureNode decode(Path data, Path schema) throws Exception {
		synchronized (LOCK) {
			// Every node DRB opened, closed afterwards so it lets go of the files (see close()).
			List<Object> opened = new ArrayList<>();
			try {
				Api api = Api.get();
				Object dataNode = api.open(data);
				opened.add(dataNode);
				Object root;
				if (schema == null) {
					Object impl = api.resolveImpl.invoke(null, dataNode);
					if (impl == null) {
						throw new StructureInterpretationException(
								"DRB did not recognise the format of the data (no DRB implementation matched it)");
					}
					root = api.implOpen.invoke(impl, dataNode);
				} else {
					Object schemaFile = api.open(schema);
					opened.add(schemaFile);
					Object schemaNode = api.xmlOpen.invoke(api.xmlFactory.getConstructor().newInstance(), schemaFile);
					opened.add(schemaNode);
					root = api.sdfOpen.invoke(api.sdfFactory.getConstructor().newInstance(), dataNode, schemaNode,
							schema.toAbsolutePath().toString());
					// SdfFactory wraps the schema's root element in a node standing for the data file itself.
					if (root != null && (Integer) api.getChildrenCount.invoke(root) == 1) {
						opened.add(root);
						root = api.getChildAt.invoke(root, 0);
					}
				}
				if (root == null) {
					throw new StructureInterpretationException("DRB could not apply the SDF schema " + schema
							+ " to the data - DRB's own messages about why are in the application log");
				}
				opened.add(root);
				return copy(api, root, new int[1], java.nio.file.Files.size(data));
			} catch (InvocationTargetException e) {
				Throwable cause = e.getCause() == null ? e : e.getCause();
				throw new StructureInterpretationException("DRB failed: " + cause, cause);
			} finally {
				for (int i = opened.size() - 1; i >= 0; i--) {
					close(opened.get(i));
				}
			}
		}
	}

	/**
	 * Releases a DRB node's files. No DRB interface declares it, but DRB's node
	 * implementations (file nodes, SDF blocks, XML nodes) have a public
	 * {@code close(boolean deep)}; without it Windows can't delete the files.
	 */
	private static void close(Object node) {
		try {
			Method close = node.getClass().getMethod("close", boolean.class);
			close.setAccessible(true);
			close.invoke(node, true);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			// Nothing to release, or already released.
		}
	}

	private static StructureNode copy(Api api, Object node, int[] count, long dataSize) throws Exception {
		if (++count[0] > MAX_NODES) {
			throw new StructureInterpretationException("DRB's tree has more than " + MAX_NODES + " nodes");
		}
		String name = String.valueOf(api.getName.invoke(node));
		int childCount = (Integer) api.getChildrenCount.invoke(node);
		Map<String, Object> attributes = attributes(api, node);
		// DRB doesn't itself fail on data shorter than the schema describes; it
		// reports positions past the end instead, and reads garbage there.
		if (attributes.get("offset") instanceof Number offset && attributes.get("length") instanceof Number length
				&& offset.longValue() + length.longValue() > dataSize) {
			throw new StructureInterpretationException("The data is " + dataSize + " bytes, but '" + name
					+ "' needs bytes " + offset.longValue() + " to " + (offset.longValue() + length.longValue() - 1));
		}
		DefaultStructureNode.Builder builder = DefaultStructureNode.builder(name,
				childCount == 0 ? StructureNodeKind.LEAF : StructureNodeKind.COMPOSITE);
		Object namespace = api.getNamespaceURI.invoke(node);
		if (namespace != null && !namespace.toString().isEmpty()) {
			builder.typeName(namespace.toString());
		}
		builder.attributes(attributes);
		byteRange(attributes).ifPresent(builder::sourceRange);
		if (childCount == 0) {
			Object value = api.getValue.invoke(node);
			if (value != null) {
				builder.value(api.convert(value, (Integer) api.getValueType.invoke(node)));
			}
		} else {
			for (int i = 0; i < childCount; i++) {
				builder.addChild(copy(api, api.getChildAt.invoke(node, i), count, dataSize));
			}
		}
		return builder.build();
	}

	private static Map<String, Object> attributes(Api api, Object node) throws Exception {
		Map<String, Object> result = new LinkedHashMap<>();
		Object list = api.getAttributes.invoke(node);
		if (list == null) {
			// DRB 2.5's SDF nodes list no attributes but still answer for these by name.
			for (String attributeName : NAMED_ATTRIBUTES) {
				Object attribute = api.getAttribute.invoke(node, attributeName);
				Object value = attribute == null ? null : api.attrGetValue.invoke(attribute);
				if (value != null) {
					result.put(attributeName, api.convert(value, (Integer) api.attrGetValueType.invoke(attribute)));
				}
			}
			return result;
		}
		int length = (Integer) api.attrListLength.invoke(list);
		for (int i = 0; i < length; i++) {
			Object attribute = api.attrListItem.invoke(list, i);
			Object value = api.attrGetValue.invoke(attribute);
			result.put(String.valueOf(api.attrGetName.invoke(attribute)),
					value == null ? null : api.convert(value, (Integer) api.attrGetValueType.invoke(attribute)));
		}
		return result;
	}

	/**
	 * DRB's SDF implementation reports {@code offset}/{@code length} (in
	 * bytes, offsets absolute) on each node it decoded.
	 */
	private static java.util.Optional<ByteRange> byteRange(Map<String, Object> attributes) {
		if (attributes.get("offset") instanceof Number offset && attributes.get("length") instanceof Number length
				&& offset.longValue() >= 0 && length.longValue() > 0) {
			return java.util.Optional.of(ByteRange.ofBytes(offset.longValue(), length.longValue()));
		}
		return java.util.Optional.empty();
	}

	/** Resolved once: the reflective handles on DRB's API. */
	private static final class Api {

		private static volatile Api instance;

		final Method factoryOpen;
		final Method resolveImpl;
		final Method implOpen;
		final Class<?> xmlFactory;
		final Method xmlOpen;
		final Class<?> sdfFactory;
		final Method sdfOpen;
		final Method getName;
		final Method getNamespaceURI;
		final Method getValue;
		final Method getValueType;
		final Method getChildrenCount;
		final Method getChildAt;
		final Method getAttributes;
		final Method getAttribute;
		final Method attrListLength;
		final Method attrListItem;
		final Method attrGetName;
		final Method attrGetValue;
		final Method attrGetValueType;
		/** DRB value type id (the {@code *_ID} constants on {@code fr.gael.drb.value.Value}) -> its constant name. */
		final Map<Integer, String> valueTypeNames = new HashMap<>();

		private Api() throws ReflectiveOperationException {
			Class<?> node = Class.forName("fr.gael.drb.DrbNode");
			Class<?> factoryImpl = Class.forName("fr.gael.drb.DrbFactoryImpl");
			Class<?> attrList = Class.forName("fr.gael.drb.DrbAttributeList");
			Class<?> attribute = Class.forName("fr.gael.drb.DrbAttribute");
			factoryOpen = Class.forName("fr.gael.drb.DrbFactory").getMethod("openURI", String.class);
			resolveImpl = Class.forName("fr.gael.drb.impl.DrbFactoryResolver").getMethod("resolveImpl", node);
			implOpen = factoryImpl.getMethod("open", node);
			xmlFactory = Class.forName("fr.gael.drb.impl.xml.XmlFactory");
			xmlOpen = factoryImpl.getMethod("open", node);
			sdfFactory = Class.forName("fr.gael.drb.impl.sds.SdfFactory");
			sdfOpen = sdfFactory.getMethod("open", node, node, String.class);
			getName = node.getMethod("getName");
			getNamespaceURI = node.getMethod("getNamespaceURI");
			getValue = node.getMethod("getValue");
			getValueType = node.getMethod("getValueType");
			getChildrenCount = node.getMethod("getChildrenCount");
			getChildAt = node.getMethod("getChildAt", int.class);
			getAttributes = node.getMethod("getAttributes");
			getAttribute = node.getMethod("getAttribute", String.class);
			attrListLength = attrList.getMethod("getLength");
			attrListItem = attrList.getMethod("item", int.class);
			attrGetName = attribute.getMethod("getName");
			attrGetValue = attribute.getMethod("getValue");
			attrGetValueType = attribute.getMethod("getValueType");
			// Several of these are declared on DRB's non-public DrbSimpleNode interface; DRB
			// sits in the unnamed module on the classpath, so making them accessible is allowed.
			for (Method method : new Method[] {getName, getNamespaceURI, getValue, getValueType, getChildrenCount,
					getChildAt, getAttributes, getAttribute, attrListLength, attrListItem, attrGetName, attrGetValue, attrGetValueType,
					implOpen, xmlOpen, sdfOpen, factoryOpen, resolveImpl}) {
				method.setAccessible(true);
			}
			for (Field field : Class.forName("fr.gael.drb.value.Value").getFields()) {
				if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class && field.getName().endsWith("_ID")) {
					valueTypeNames.put(field.getInt(null), field.getName());
				}
			}
		}

		static Api get() throws ReflectiveOperationException {
			Api api = instance;
			if (api == null) {
				api = new Api();
				instance = api;
			}
			return api;
		}

		Object open(Path file) throws ReflectiveOperationException {
			Object opened = factoryOpen.invoke(null, file.toAbsolutePath().toString());
			if (opened == null) {
				throw new StructureInterpretationException("DRB could not open " + file);
			}
			return opened;
		}

		/**
		 * DRB values ({@code fr.gael.drb.value.Value}) as plain Java values, by
		 * their declared type: integers as {@link Long} (or {@link BigInteger}
		 * past its range), floating point as {@link Double}, decimals as
		 * {@link BigDecimal}, booleans as {@link Boolean}, anything else as its text.
		 */
		Object convert(Object value, int valueType) {
			String text = value.toString();
			String type = valueTypeNames.getOrDefault(valueType, "");
			try {
				if (type.contains("DURATION") || type.contains("DATE")) {
					return text;
				}
				if (type.contains("FLOAT") || type.contains("DOUBLE")) {
					return Double.valueOf(text);
				}
				if (type.contains("DECIMAL") || type.equals("NUMERIC_ID")) {
					return new BigDecimal(text.strip());
				}
				if (type.equals("BOOLEAN_ID")) {
					return Boolean.valueOf(text.strip());
				}
				if (type.contains("BYTE") || type.contains("SHORT") || type.contains("INT") || type.contains("LONG")) {
					BigInteger big = new BigInteger(text.strip());
					return big.bitLength() < 64 ? (Object) big.longValue() : big;
				}
			} catch (NumberFormatException e) {
				// Fall through: keep DRB's own text rather than guess.
			}
			return text;
		}
	}
}
