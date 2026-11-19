@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  echo Python 3.10 or later is required. This launcher does not install Python.
  pause
  exit /b 1
)
set /p LD_SERVER=HTTPS LensDrop server origin: 
set /p LD_FOLDER=Full watch folder path: 
set /p LD_STATE=Queue state folder outside the watch folder: 
py -3 lensdrop_agent.py --server "%LD_SERVER%" --folder "%LD_FOLDER%" --state "%LD_STATE%"
pause
