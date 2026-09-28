# Third-party libraries not on Maven Central

`maven-repo/` is a small file-based Maven repository, declared in the root
`pom.xml` (and in `archive-manager/pom.xml`), for libraries this project needs
that are not published to Maven Central.

## DRB 2.5.13 (`fr.gael.drb:drb:2.5.13`)

GAEL Consultant's Java **Data Request Broker** API, used by
`oais-structure-drb` to decode data with DRB "SDF" schemas and by
archive-manager's RepInfo Tools to test generated DRB descriptions.

- **Licence:** GNU Lesser General Public License v3 (LGPL-3.0-or-later), as
  stated in every source file's header and in DRB's own POM. The licence texts
  are in `licenses/` (`COPYING.LESSER`; LGPL v3 is a set of additional
  permissions on top of the GPL v3 in `COPYING`).
- **Source:** the corresponding source code is shipped alongside the binary, as
  `maven-repo/fr/gael/drb/drb/2.5.13/drb-2.5.13-sources.jar`.
- **Unmodified:** both jars are exactly as distributed by GAEL; only the POM
  here was written for this repository (see the comment in it for how its
  dependencies differ from DRB's own).
- **Runtime dependencies:** `net.sf.ehcache:ehcache-core:2.5.0` (Apache 2.0,
  Maven Central). DRB logs through log4j 1.x's `Logger`/`Category` API; this
  project routes that to SLF4J with `org.slf4j:log4j-over-slf4j` rather than
  shipping log4j 1.2. DRB's optional `fr.gael.streams:streams` dependency is
  not included: it is only used to read from non-file streams, which this
  project never asks DRB to do.

archive-manager's executable jar bundles the DRB jar (an LGPL library, used
unmodified and replaceable). This README, the licence texts and the sources
jar are what satisfy LGPL v3's notice and source requirements for that.

## Kaitai Struct compiler 0.11 (`io.kaitai:kaitai-struct-compiler_2.13:0.11`)

The compiler that turns a Kaitai Struct `.ksy` description into source code.
archive-manager's RepInfo Tools uses it to test a generated `.ksy` against a
sample file. Unlike DRB it *is* on Maven Central, so it isn't in `maven-repo/`;
`archive-manager/pom.xml` copies it into the jar at build time.

- **Licence:** GNU General Public License v3 (GPL-3.0), as stated in its POM.
  The licence text is `licenses/COPYING`. Its own dependencies are
  permissively licensed: `scala-library` 2.13.13 (Apache 2.0), `scopt` 4.1.0,
  `fastparse` 2.3.3, `sourcecode` 0.2.3 and `geny` 0.6.10 (MIT), and
  `snakeyaml` 2.0 (Apache 2.0).
- **A separate program, not a library.** The compiler's jars are bundled as
  plain files under `kaitai-compiler/` in archive-manager's jar, not on its
  classpath. `KaitaiSampleRunner` extracts them to a temporary directory and
  runs the compiler as its own Java process (`io.kaitai.struct.JavaMain`),
  passing it a `.ksy` file and reading back the Java source it writes - the
  same arm's-length use as running it from the command line. archive-manager
  never links to the compiler's code.
- **Source:** the corresponding source is shipped in the jar too, as
  `META-INF/third-party/kaitai-struct-compiler/kaitai-struct-compiler_2.13-0.11-sources.jar`
  (the unmodified sources jar from Maven Central); upstream is
  https://github.com/kaitai-io/kaitai_struct_compiler.
- **Unmodified**, exactly as published on Maven Central.
- **Generated code isn't covered by the GPL.** The Java classes the compiler
  writes, and the Kaitai Struct runtime they use (`kaitai-struct-runtime`,
  MIT), are what archive-manager actually runs.

## Eclipse Compiler for Java 3.40.0 (`org.eclipse.jdt:ecj:3.40.0`)

Compiles the Java that the Kaitai Struct compiler generates when the server
runs a JRE rather than a JDK (a JDK's own compiler is used when present).
Eclipse Public License 2.0, from Maven Central, used unmodified as an
ordinary library; the jar carries its own `about.html` licence notice.
