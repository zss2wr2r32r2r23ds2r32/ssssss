@echo off
setlocal
cd /d "%~dp0"

where AutoHotkey64.exe >nul 2>&1
if %ERRORLEVEL%==0 (
    start "" AutoHotkey64.exe "%~dp0pine_macro.ahk"
    exit /b 0
)

where AutoHotkey.exe >nul 2>&1
if %ERRORLEVEL%==0 (
    start "" AutoHotkey.exe "%~dp0pine_macro.ahk"
    exit /b 0
)

if exist "%ProgramFiles%\AutoHotkey\v2\AutoHotkey64.exe" (
    start "" "%ProgramFiles%\AutoHotkey\v2\AutoHotkey64.exe" "%~dp0pine_macro.ahk"
    exit /b 0
)

if exist "%ProgramFiles%\AutoHotkey\v2\AutoHotkey32.exe" (
    start "" "%ProgramFiles%\AutoHotkey\v2\AutoHotkey32.exe" "%~dp0pine_macro.ahk"
    exit /b 0
)

if exist "%LocalAppData%\Programs\AutoHotkey\v2\AutoHotkey64.exe" (
    start "" "%LocalAppData%\Programs\AutoHotkey\v2\AutoHotkey64.exe" "%~dp0pine_macro.ahk"
    exit /b 0
)

echo Pine Pollen Macro needs AutoHotkey v2.
echo Install it from https://www.autohotkey.com/ then run START.bat again.
echo.
pause
exit /b 1
