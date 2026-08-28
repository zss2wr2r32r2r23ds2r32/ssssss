@echo off
setlocal
cd /d "%~dp0TuffMacro"
if exist "START.bat" (
    call START.bat %*
    exit /b %ERRORLEVEL%
)
echo Tuff Macro folder is missing. Extract TuffMacro.zip and keep this START.bat next to the TuffMacro folder.
pause
exit /b 1
