#!/bin/bash
# One-time local install of splat.jar and every jar its own MANIFEST.MF
# Class-Path declares (plus jhall.jar, a transitive dependency of help.jar
# not listed there), into the local Maven repo -- see the root README's
# "SPLAT example description" section for why: unlike stil, splat is not
# published on Maven Central, so this mirrors exactly what the starjava
# Ant build's own manifest already computed as the required closure,
# nothing guessed. (Except ttools.jar, which the manifest names but SPLAT-VO
# doesn't ship: SPLAT runs without it.)
#
# The jars come from either
#   - an installed SPLAT-VO (the usual way: the installer from
#     https://github.com/mmpcn/splat-build/releases, see
#     http://www.g-vo.org/pmwiki/About/SPLAT) -- set SPLAT_HOME to the folder
#     it was installed in, the one holding bin/, etc/ and lib/; or
#   - a from-source starjava build with splat installed -- set STARJAVA_LIB
#     to its lib/ directory (see the root README for the recipe).
#
# Usage: SPLAT_HOME=/path/to/splat-vo ./install-splat-deps.sh
#        STARJAVA_LIB=/path/to/starjava/lib ./install-splat-deps.sh
set -e

if [ -n "${SPLAT_HOME:-}" ]; then
  LIB="$SPLAT_HOME/lib"
elif [ -n "${STARJAVA_LIB:-}" ]; then
  LIB="$STARJAVA_LIB"
else
  echo "Set SPLAT_HOME to an installed SPLAT-VO (or STARJAVA_LIB to a starjava build's lib/ directory)." >&2
  exit 1
fi
GROUP="info.oais.infomodel.starjava.local"
VERSION="0.0.1-local"

if [ ! -f "$LIB/splat/splat.jar" ]; then
  echo "splat.jar not found under $LIB -- is that a SPLAT-VO installation (or a starjava lib/ directory)?" >&2
  exit 1
fi

install_one() {
  local file="$1"
  local artifact="$2"
  mvn -q install:install-file \
    -Dfile="$file" \
    -DgroupId="$GROUP" \
    -DartifactId="$artifact" \
    -Dversion="$VERSION" \
    -Dpackaging=jar \
    -DgeneratePom=true
  echo "installed $artifact"
}

install_one "$LIB/splat/splat.jar" "splat"

# Same-directory third-party jars from splat.jar's manifest Class-Path
for j in splat_lines.jar bsh-1.2b7.jar splat_colt.jar jdom.jar splat_help.jar \
         splat_examples.jar jsci_core.jar jsci_wavelet.jar jargs.jar toolbar.jar \
         epsgraphics.jar; do
  name="${j%.jar}"
  install_one "$LIB/splat/$j" "$name"
done

# Sibling starjava module jars
for m in astgui ndx hdx array tamfits jniast soapserver fits hds pal diva help \
         table votable jsky datanode vo jsamp; do
  install_one "$LIB/$m/$m.jar" "$m"
done

# jhall.jar: not in splat.jar's own manifest, but a transitive dependency of
# help.jar (JavaHelp, javax.help.*), needed as soon as SplatBrowser builds
# its Help menu.
install_one "$LIB/help/jhall.jar" "jhall"

# contrib/ third-party jars (VAMDC support)
for j in jaxb2-basics-runtime-0.5.3.jar xsams-1.0.jar xsams-io-12.07.jar \
         mappings-12.07.jar registry-client-light-12.07.jar Dictionaries-12.07.jar; do
  name="${j%.jar}"
  install_one "$LIB/contrib/$j" "$name"
done

echo "ALL INSTALLED"
