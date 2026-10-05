SET CLASSPATH=%CLASSPATH%;./libs/*

SET CLASSPATH=%CLASSPATH%;./config/
SET CLASSPATH=%CLASSPATH%;./*
SET CLASSPATH=%CLASSPATH%;.

REM Operators edit config\logback.xml. The copy inside the jars is not the runtime configuration.
set "JAVA_LOGGING_OPTS=-Dlogback.configurationFile=config/logback.xml"

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

set "JAVA_AGENT_OPTS="
if /I "%L2JFREE_OTEL_ENABLED%"=="true" (
	if not defined OTEL_EXPORTER_OTLP_ENDPOINT (
		echo OpenTelemetry is enabled but OTEL_EXPORTER_OTLP_ENDPOINT is not set.
		exit /b 1
	)
	if not exist "agents\opentelemetry-javaagent.jar" (
		echo OpenTelemetry agent is missing from the GameServer distribution.
		exit /b 1
	)
	if not defined OTEL_SERVICE_NAME set "OTEL_SERVICE_NAME=l2jfree-gameserver"
	set "OTEL_METRICS_EXPORTER=otlp"
	set "OTEL_TRACES_EXPORTER=otlp"
	set "OTEL_LOGS_EXPORTER=none"
	set "JAVA_AGENT_OPTS=-javaagent:agents\opentelemetry-javaagent.jar"
)
