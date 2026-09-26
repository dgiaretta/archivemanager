@echo off
REM Opens one of this directory's demo fixtures in TOPCAT via
REM OaisStructureTableBuilder -- see the root README's "TOPCAT example
REM description" section for the full explanation. Location-independent:
REM works from wherever the repo is cloned, since every path below is
REM computed from this script's own location (%~dp0), not hard-coded.
REM
REM One-time setup before the first run (from the oais-structure-topcat
REM module directory):
REM   mvn dependency:copy-dependencies -DincludeScope=runtime
REM   mvn package
REM
REM Also needs a local copy of TOPCAT's standalone jar, named
REM topcat-full.jar, in the repository root (get it from
REM https://www.star.bris.ac.uk/~mbt/topcat/) -- kept out of git via
REM .gitignore since it is a large third-party download, not this
REM project's own code.
REM
REM Usage:
REM   run-in-topcat.bat                 (defaults to point.bin, one row)
REM   run-in-topcat.bat points.csv      (10-row DFDL-described example)
REM   run-in-topcat.bat points-kaitai.csv   (the same 10 rows, via Kaitai)

setlocal

set "HERE=%~dp0"
set "MODULE_DIR=%HERE%..\..\.."
set "REPO_ROOT=%MODULE_DIR%\.."
set "TOPCAT_JAR=%REPO_ROOT%\topcat-full.jar"

set "DATA_FILE=%~1"
if "%DATA_FILE%"=="" set "DATA_FILE=%HERE%point.bin"

REM This machine's default `java` on PATH resolves to an old Java 8, which
REM cannot load this module's classes (UnsupportedClassVersionError). Prefer
REM JAVA17_HOME if set (portable across machines), else fall back to the
REM known-good JDK 21 install already used throughout this project's own
REM setup on this machine, else give up and use plain `java` (which will
REM fail loudly with the same UnsupportedClassVersionError if it's too old).
set "KNOWN_GOOD_JDK=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
if defined JAVA17_HOME (
    set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
) else if exist "%KNOWN_GOOD_JDK%\bin\java.exe" (
    set "JAVA_EXE=%KNOWN_GOOD_JDK%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

if not exist "%TOPCAT_JAR%" (
    echo %TOPCAT_JAR% not found -- download TOPCAT's standalone jar there first.
    exit /b 1
)
if not exist "%MODULE_DIR%\target\dependency" (
    echo %MODULE_DIR%\target\dependency not found -- run "mvn dependency:copy-dependencies -DincludeScope=runtime" in oais-structure-topcat first.
    exit /b 1
)

"%JAVA_EXE%" -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder -cp "%TOPCAT_JAR%;%MODULE_DIR%\target\oais-structure-topcat-0.0.1-SNAPSHOT.jar;%MODULE_DIR%\target\dependency\*" uk.ac.starlink.topcat.Driver "%DATA_FILE%"
