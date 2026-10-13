@echo off
rem ZakazPro: build the Windows package. Safe to run from anywhere.
rem Usage: installer\build-installer.cmd [--skip-exe] [--keep-temp] [--verbose]
setlocal
cd /d "%~dp0.."

rem The packaging outputs are large (100+ MB). OneDrive or an antivirus scan can keep a lock
rem on a freshly written file for a few seconds, which would make "mvn clean" fail before the
rem build even starts. Remove them first, retrying a few times.
call :deleteFolder target\installer
call :deleteFolder target\runtime
call :deleteFolder target\dist
call :deleteFolder target\smoke

rem Maven first: "clean" would delete the classes of a running Java process.
call mvn -l build.log clean verify || exit /b 1

java -cp target\classes com.mebelsex.release.InstallerBuilder %*
exit /b %ERRORLEVEL%

:deleteFolder
if not exist "%~1" exit /b 0
for /L %%i in (1,1,6) do (
    rmdir /s /q "%~1" 2>nul
    if not exist "%~1" exit /b 0
    rem "timeout" needs a console and fails in a piped build; ping is a reliable delay.
    ping -n 3 127.0.0.1 >nul
)
echo WARNING: could not delete "%~1" - a file is locked by another program.
exit /b 0
