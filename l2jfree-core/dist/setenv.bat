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
