@echo off
setlocal
set "JAVA_HOME=D:\jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0gradlew.bat" runClient
endlocal
