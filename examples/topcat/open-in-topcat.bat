@echo off
REM Opens the bright star catalogue in TOPCAT through its Representation
REM Information manifest -- see README.md in this folder. Paths are worked out
REM from this script's own location, so it runs from anywhere.
REM
REM   open-in-topcat.bat                      (this example)
REM   open-in-topcat.bat other-manifest.ttl   (any manifest file or URL, #name to pick one)

setlocal
set "HERE=%~dp0"
set "REPO=%HERE%..\.."
set "PLUGIN=%REPO%\oais-structure-topcat\target"
set "MANIFEST=%~1"
if "%MANIFEST%"=="" set "MANIFEST=%HERE%bright-star-catalogue.ttl"

set "JAVA_EXE=java"
if defined JAVA17_HOME set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
if not defined JAVA17_HOME if exist "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

if not exist "%REPO%\topcat-full.jar" (
    echo topcat-full.jar isn't in the repository root: see README.md, "Installing".
    exit /b 1
)
if not exist "%PLUGIN%\dependency" (
    echo The TOPCAT reader's dependencies aren't collected yet: see README.md, "Installing".
    exit /b 1
)

"%JAVA_EXE%" -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder -cp "%REPO%\topcat-full.jar;%PLUGIN%\oais-structure-topcat-0.0.1-SNAPSHOT.jar;%PLUGIN%\dependency\*" uk.ac.starlink.topcat.Driver -f OAIS-RepInfo "%MANIFEST%"
