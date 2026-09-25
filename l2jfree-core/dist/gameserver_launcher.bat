@echo off
title Game Server Console
:start
echo Starting l2jfree
echo.

SET OLDCLASSPATH=%CLASSPATH%
call setenv.bat

REM -------------------------------------
REM Default parameters for a basic server.
"%JAVA_CMD%" -Xms512m -Xmx1024m -server com.l2jfree.gameserver.GameServer
REM -------------------------------------

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
