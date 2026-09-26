@echo off
REM Opens one of this directory's demo fixtures as a spectrum in a live SPLAT
REM window, via OaisStructureSpectrumLauncher -- see the root README's "SPLAT
REM example description" section for the full explanation. Location-
REM independent: works from wherever the repo is cloned, since every
REM in-repo path below is computed from this script's own location (%~dp0).
REM
REM One-time setup before the first run:
REM   1. Build and install splat.jar and its dependency closure into the
REM      local Maven repo -- see scripts/install-splat-deps.sh alongside
REM      this module's pom.xml (needs a from-source starjava build first;
REM      splat, unlike stil, is not published on Maven Central).
REM   2. From the oais-structure-splat module directory:
REM        mvn dependency:copy-dependencies -DincludeScope=runtime
REM        mvn package
REM
REM Usage:
REM   run-in-splat.bat                (defaults to spectrum.csv, 10 rows)
REM   run-in-splat.bat spectrum.csv

setlocal

set "HERE=%~dp0"
set "MODULE_DIR=%HERE%..\..\.."

set "DATA_FILE=%~1"
if "%DATA_FILE%"=="" set "DATA_FILE=%HERE%spectrum.csv"

REM This machine's default `java` on PATH resolves to an old Java 8, which
REM cannot load this module's classes (UnsupportedClassVersionError). Prefer
REM JAVA17_HOME if set (portable across machines), else fall back to the
REM known-good JDK already used throughout this project's own setup on this
REM machine, else give up and use plain `java`.
set "KNOWN_GOOD_JDK=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
if defined JAVA17_HOME (
    set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
) else if exist "%KNOWN_GOOD_JDK%\bin\java.exe" (
    set "JAVA_EXE=%KNOWN_GOOD_JDK%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

REM jniast's native library (SPLAT's AST/WCS support, loaded as soon as any
REM spectrum is built) has no Maven Central presence and was never copied
REM into the "installed" starjava/lib tree by "ant install" either -- it
REM only exists under the source checkout's own jniast/lib/<arch>. Override
REM with JNIAST_NATIVE_DIR if your starjava checkout lives elsewhere.
if not defined JNIAST_NATIVE_DIR set "JNIAST_NATIVE_DIR=C:\Users\david\starjava\source\jniast\lib\amd64"

if not exist "%MODULE_DIR%\target\dependency" (
    echo %MODULE_DIR%\target\dependency not found -- run "mvn dependency:copy-dependencies -DincludeScope=runtime" in oais-structure-splat first.
    exit /b 1
)

"%JAVA_EXE%" -Djava.library.path="%JNIAST_NATIVE_DIR%" -cp "%MODULE_DIR%\target\oais-structure-splat-0.0.1-SNAPSHOT.jar;%MODULE_DIR%\target\dependency\*" info.oais.infomodel.structure.splat.OaisStructureSpectrumLauncher "%DATA_FILE%"
