@echo off
setlocal
cd /d "%~dp0"

:: Prefer the AutoHotkey collector when AHK v2 is installed (like Natro's START.bat).
:: Otherwise fall back to the .exe launcher that opens Bee Swarm Simulator.

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

if exist "%~dp0PinePollenMacro.exe" (
    start "" "%~dp0PinePollenMacro.exe"
    exit /b 0
)
if exist "%~dp0dist\PinePollenMacro.exe" (
    start "" "%~dp0dist\PinePollenMacro.exe"
    exit /b 0
)

echo Pine Pollen Macro needs AutoHotkey v2 to run pine_macro.ahk
echo Install it from https://www.autohotkey.com/ then run START.bat again.
echo.
echo Or double-click PinePollenMacro.exe to open Bee Swarm Simulator.
echo.
pause
exit /b 1
