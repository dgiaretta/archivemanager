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
# Usage: ./run.sh   (chmod +x first) -- from the project root, or from any
# folder holding a copy of the jar next to this script (e.g. on a server).
# Needs Java 17+; uses $JAVA_HOME/bin/java when JAVA_HOME is set.
# Extra arguments are passed to the app, e.g. ./run.sh --server.port=8080

JAR_NAME="archive-manager-0.1.0.jar"
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$DIR/$JAR_NAME"
[ -f "$JAR_PATH" ] || JAR_PATH="$DIR/target/$JAR_NAME"

JAVA_EXE="java"
[ -n "$JAVA_HOME" ] && JAVA_EXE="$JAVA_HOME/bin/java"

exec "$JAVA_EXE" -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "$JAR_PATH" "$@"
