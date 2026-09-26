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
REM Usage: run.bat  (from the project root, or from any folder holding a copy
REM of the jar next to this script -- e.g. on a deployment server)
REM Needs Java 17+. Uses %JAVA_HOME%\bin\java.exe when JAVA_HOME is set, since
REM the "java" first on PATH is often an older Java 8 (Oracle's java8path shim).
REM Set ARCHIVE_EDIT_PASSWORD before launching if you want a real edit password.

setlocal
set JAR_NAME=archive-manager-0.1.0.jar
set JAR_PATH=%~dp0%JAR_NAME%
if not exist "%JAR_PATH%" set JAR_PATH=%~dp0target\%JAR_NAME%

set JAVA_EXE=java
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -jar "%JAR_PATH%" %*
