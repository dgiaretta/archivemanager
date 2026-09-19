@echo off
setlocal enabledelayedexpansion

set "BASE=C:\Users\david\Dropbox\Giaretta Associates\GA-bids\Maldives National Archives\PHASE 3\DR_David_Testes"
cd %BASE%

set "OUT=%BASE%\extracted"

if not exist "%OUT%" mkdir "%OUT%"

for %%F in ("%BASE%\*.7z") do (
    set "ARCHIVE=%%~fF"
    set "NAME=%%~nF"

    echo Processing: %%~nF.7z

    rem Build the internal folder path exactly as in the archive:
    rem NAME\data\objects
    "C:\Program Files\7-Zip\7z.exe" e -y -o"%OUT%" "%ARCHIVE%" "%NAME%\data\objects\*.pdf" "%NAME%\data\objects\*.jpg"
)

echo Done.