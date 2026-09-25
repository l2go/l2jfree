@echo off
title Login Server Console
:start
echo Starting L2J Login Server.
echo.

SET OLDCLASSPATH=%CLASSPATH%
call setenv.bat

"%JAVA_CMD%" -Dfile.encoding=UTF-8 -Xmx64m com.l2jfree.loginserver.LoginServer

SET CLASSPATH=%OLDCLASSPATH%

if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
goto end
:restart
echo.
echo Admin Restart ...
echo.
goto start
:error
echo.
echo Server terminated abnormaly
echo.
:end
echo.
echo server terminated
echo.
pause
