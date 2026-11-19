@echo off
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (
  py -3 lensdrop_console.py
) else (
  python lensdrop_console.py
)
if errorlevel 1 (
  echo.
  echo Install Python 3.10 or later with Tcl/Tk. This source launcher is not an installer.
  pause
)
