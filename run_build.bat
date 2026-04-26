@echo off
setlocal
for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\resolve_java_home.ps1"`) do set "JAVA_HOME=%%I"
if not defined JAVA_HOME (
    echo Failed to locate a JDK. Please install JDK 21 or set JAVA_HOME manually.
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
call "%~dp0gradlew.bat" build %*
endlocal
