@echo off
REM Launches archive-manager with the JVM's default charset explicitly forced to
REM UTF-8. This matters specifically on Windows with Java 17 (which predates
REM Java 18's "UTF-8 by default" change, JEP 400): without these flags, an
REM unconfigured JVM's default charset follows the Windows ANSI code page,
REM which has no representation for Thaana (Dhivehi) script at all -- anything
REM in the JVM or its libraries that doesn't explicitly specify UTF-8 for a
REM given operation falls back to that platform default and silently
REM substitutes "?" for characters it can't represent. See README's
REM internationalisation section and /diagnostics/encoding for how to confirm
REM this is (or isn't) the cause on your machine.
REM
REM Usage: run.bat  (from the project root)
REM The jar is produced by Maven under target/, not in the project root.
REM Set ARCHIVE_EDIT_PASSWORD before launching if you want a real edit password.

set JAR_NAME=target\archive-manager-0.1.0.jar

java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "%JAR_NAME%"
