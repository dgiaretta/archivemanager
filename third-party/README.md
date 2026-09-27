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
