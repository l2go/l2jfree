SET CLASSPATH=%CLASSPATH%;./libs/*

SET CLASSPATH=%CLASSPATH%;./config/
SET CLASSPATH=%CLASSPATH%;./*
SET CLASSPATH=%CLASSPATH%;.

if defined JAVA_HOME (
	set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
	set "JAVA_CMDW=%JAVA_HOME%\bin\javaw.exe"
) else (
	set "JAVA_CMD=java"
	set "JAVA_CMDW=javaw"
)

set "JAVA_VERSION="
set "JAVA_MAJOR="
for /f "tokens=3" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~v"
for /f "tokens=1 delims=." %%v in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%v"
if not "%JAVA_MAJOR%"=="25" (
	echo Java 25 is required; Microsoft Build of OpenJDK 25 is the deployment target. Detected version: %JAVA_VERSION%
	exit /b 1
)
