@echo off
setlocal

set ROOT_DIR=%~dp0\..
cd /d "%ROOT_DIR%"

if "%~1"=="" (
  echo Usage:
  echo   scripts\attach_dropbox_storage_links.bat --dir "C:/Users/me/Dropbox/records" --base-url "https://www.dropbox.com/scl/fi/abc123/records" [--tdb data/tdb2] [--dry-run]
  exit /b 1
)

mvn -q -DskipTests compile >NUL

set CP_FILE=.attach_dropbox_storage_links.classpath
if exist "%CP_FILE%" del /f /q "%CP_FILE%"
mvn -q -DskipTests dependency:build-classpath -Dmdep.outputFile="%CP_FILE%" >NUL
for /f "delims=" %%i in ('type "%CP_FILE%"') do set CP=target/classes;%%i
java -cp "%CP%" info.oais.archive.manager.tools.AttachDropboxStorageLinks %*
if exist "%CP_FILE%" del /f /q "%CP_FILE%"
