@echo off
REM Writes the galaxy field image as FITS through its Representation
REM Information manifest, then opens it in an image viewer if you name one --
REM see README.md in this folder. Paths are worked out from this script's own
REM location, so it runs from anywhere.
REM
REM   open-as-fits.bat                                 (this example, to galaxy-field.fits)
REM   open-as-fits.bat other-manifest.ttl out.fits     (any manifest file or URL, #name to pick one)
REM
REM VIEWER, if set, is run with the FITS file as its argument, e.g.
REM   set "VIEWER=C:\Fiji.app\ImageJ-win64.exe"
REM   set "VIEWER=C:\SAOImageDS9\ds9.exe"

setlocal
set "HERE=%~dp0"
set "REPO=%HERE%..\.."
set "IMAGE=%REPO%\oais-structure-image\target\oais-structure-image-0.0.1-SNAPSHOT.jar"
set "READER=%REPO%\oais-structure-topcat\target"
set "MANIFEST=%~1"
if "%MANIFEST%"=="" set "MANIFEST=%HERE%galaxy-field.ttl"
set "FITS=%~2"
if "%FITS%"=="" set "FITS=%HERE%galaxy-field.fits"

set "JAVA_EXE=java"
if defined JAVA17_HOME set "JAVA_EXE=%JAVA17_HOME%\bin\java.exe"
if not defined JAVA17_HOME if exist "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe" set "JAVA_EXE=C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe"

if not exist "%IMAGE%" (
    echo oais-structure-image isn't built yet: see README.md, "Installing".
    exit /b 1
)
if not exist "%READER%\dependency" (
    echo The manifest reader's dependencies aren't collected yet: see README.md, "Installing".
    exit /b 1
)

"%JAVA_EXE%" -cp "%IMAGE%;%READER%\oais-structure-topcat-0.0.1-SNAPSHOT.jar;%READER%\dependency\*" info.oais.infomodel.structure.image.ManifestToFits "%MANIFEST%" "%FITS%" || exit /b 1
if defined VIEWER (
    start "" "%VIEWER%" "%FITS%"
) else (
    echo Open it in Fiji/ImageJ, SAOImage DS9 or Aladin, or set VIEWER to have this script do it.
)
