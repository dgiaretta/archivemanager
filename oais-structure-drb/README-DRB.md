# oais-structure-drb notes

DRB (CNES/GAEL's "Data Request Broker") is not published to Maven Central,
and its exact Java API has evolved across the classic `fr.gael.drb`
distribution and the newer, partly open-sourced "DRB Cortex"
(`fr.gael.drb.cortex`, `drbx-*` artifacts). Because of that, this module was
written and reviewed without being able to compile it against a real DRB
jar, and takes a different shape from `oais-structure-dfdl` and
`oais-structure-kaitai` as a result:

- **No compile-time dependency on DRB at all.** `DrbStructureRepInfo`,
  `DrbStructureNode` and `DrbStructureInterpreterProvider` talk to DRB
  purely through `java.lang.reflect` (see `ReflectiveApi`). The module
  compiles and its `StructureInterpreterProvider.isAvailable()` check
  quietly returns `false` whether or not any DRB jar is present.
- **You supply the DRB jar yourself**, however your organisation is
  licensed to obtain it - a `system`-scope Maven dependency pointing at a
  local jar, or an internal repository manager entry - added to this
  module's `pom.xml` or to whatever application depends on it.
- **The method names it looks for are a best-effort guess**, based on DRB's
  long-standing public node-tree shape (a factory resolver that turns a
  stream into a root node; a node with a name, a value, children, and
  attributes) rather than a javadoc this project could fetch and pin
  against. Concretely, `DrbStructureRepInfo` expects:
  - a class named by `DrbFormatSpecification#getFactoryResolverClassName()`
    (default: `fr.gael.drb.DrbFactoryResolver`) with a static
    `getDefaultFactoryResolver()` method;
  - that resolver having a `create(InputStream)` method returning a node;
  - the node having `getName()`, `getValue()`, and either
    `getChildrenList()`/`getChildren()` and
    `getAttributesList()`/`getAttributes()` returning a `List`, an array, or
    an `Iterable`.

  If your installed DRB version's method names differ, the fix is local and
  small: adjust the candidate name lists in `DrbStructureRepInfo`
  (`create`, `getDefaultFactoryResolver`) and `DrbStructureNode`
  (`getChildrenList`/`getChildren`, `getAttributesList`/`getAttributes`,
  `getValue`, `getName`) - nothing else in this project needs to change,
  since everything above this adapter only ever sees the resulting
  `StructureNode`.

- **Stream lifetime is different from the other two adapters.** DRB is
  built to navigate large data sources lazily; `DrbStructureRepInfo`
  deliberately leaves the `DigitalObject`'s stream open when it returns,
  rather than closing it the way the eager DFDL/Kaitai adapters do. See the
  Javadoc on `DrbStructureRepInfo` for what this means for callers.

None of this is a reason not to use the module - reflection against a
small, stable, long-documented API shape is a reasonable way to depend on a
library you cannot pin a Maven coordinate to - but it does mean you should
write one small integration test against your actual DRB jar and format
before relying on this in anything that matters, rather than trusting the
method names above blindly.
