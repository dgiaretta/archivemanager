#!/bin/bash
# Launches archive-manager with the JVM's default charset explicitly forced
# to UTF-8, regardless of what the environment's locale variables (LANG,
# LC_ALL, LC_CTYPE) happen to be set to. Ubuntu/Linux generally defaults to
# a UTF-8 locale in an interactive shell, but that's not guaranteed for
# every way this jar might actually get launched -- a systemd unit, a cron
# job, a script, a container -- none of which necessarily inherit the same
# environment an interactive terminal has. If the locale isn't UTF-8 for
# whatever reason, an unconfigured JVM on Java 17 (which predates Java 18's
# "UTF-8 by default" change, JEP 400) falls back to it, and anything that
# doesn't explicitly specify UTF-8 for a given operation can silently
# substitute "?" for characters it can't represent -- Thaana (Dhivehi
# script) among them. See README's internationalisation section and
# /diagnostics/encoding to check whether this is actually the cause on your
# system rather than assuming.
#
# Usage: ./run.sh   (from the same folder as the built jar; chmod +x first)
# Edit JAR_NAME below if your built jar has a different version number.

JAR_NAME="target/archive-manager-0.1.0.jar"

java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "$JAR_NAME"
