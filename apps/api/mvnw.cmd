@ECHO OFF
SETLOCAL

SET "MAVEN_VERSION=3.9.14"
SET "BASE_DIR=%~dp0"
SET "WRAPPER_DIR=%BASE_DIR%.mvn\wrapper"
SET "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
SET "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"
SET "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
SET "ARCHIVE_PATH=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"

IF EXIST "%MAVEN_BIN%" GOTO RUN

IF NOT EXIST "%WRAPPER_DIR%" (
  mkdir "%WRAPPER_DIR%"
)

ECHO Downloading Apache Maven %MAVEN_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ARCHIVE_PATH%'"
IF ERRORLEVEL 1 EXIT /B 1

ECHO Extracting Apache Maven %MAVEN_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path '%ARCHIVE_PATH%' -DestinationPath '%WRAPPER_DIR%' -Force"
IF ERRORLEVEL 1 EXIT /B 1

DEL /Q "%ARCHIVE_PATH%" >NUL 2>NUL

:RUN
CALL "%MAVEN_BIN%" %*
EXIT /B %ERRORLEVEL%
