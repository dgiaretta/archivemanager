@echo off
REM Opens the A0 V star spectrum in SPLAT through its Representation
REM Information manifest -- see README.md in this folder. SPLAT comes from an
REM installed SPLAT-VO (SPLAT_HOME), the manifest reader from this project.
REM Paths are worked out from this script's own location, so it runs from anywhere.
REM
REM   open-in-splat.bat                      (this example)
REM   open-in-splat.bat other-manifest.ttl   (any manifest file or URL, #name to pick one)

setlocal
set "HERE=%~dp0"
set "REPO=%HERE%..\.."
set "SPLAT=%REPO%\oais-structure-splat\target"
set "READER=%REPO%\oais-structure-topcat\target"
set "MANIFEST=%~1"
if "%MANIFEST%"=="" set "MANIFEST=%HERE%a0-v-star-spectrum.ttl"

if not defined SPLAT_HOME if exist "%ProgramFiles%\splat-vo\lib\splat\splat.jar" set "SPLAT_HOME=%ProgramFiles%\splat-vo"
if not defined SPLAT_HOME if exist "%USERPROFILE%\splat-vo\lib\splat\splat.jar" set "SPLAT_HOME=%USERPROFILE%\splat-vo"
if not defined SPLAT_HOME goto nosplat
if not exist "%SPLAT_HOME%\lib\splat\splat.jar" goto nosplat
if not defined JNIAST_NATIVE_DIR set "JNIAST_NATIVE_DIR=%SPLAT_HOME%\lib\amd64"

set "JAVA_EXE=java"
if defined JAVA17_HOME set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
if not defined JAVA17_HOME if exist "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

if not exist "%SPLAT%\oais-structure-splat-0.0.1-SNAPSHOT.jar" (
    echo oais-structure-splat isn't built yet: see README.md, "Installing".
    exit /b 1
)
if not exist "%READER%\dependency" (
    echo The manifest reader's dependencies aren't collected yet: see README.md, "Installing".
    exit /b 1
)

"%JAVA_EXE%" -Djava.library.path="%JNIAST_NATIVE_DIR%" -Dsplat.etc.dir="%SPLAT_HOME%\etc\splat" -cp "%SPLAT_HOME%\lib\splat\splat.jar;%SPLAT%\oais-structure-splat-0.0.1-SNAPSHOT.jar;%READER%\oais-structure-topcat-0.0.1-SNAPSHOT.jar;%READER%\dependency\*" info.oais.infomodel.structure.splat.OaisStructureSpectrumLauncher "%MANIFEST%"
exit /b

:nosplat
echo SPLAT-VO isn't found: install it and set SPLAT_HOME (see README.md, "Installing").
exit /b 1
