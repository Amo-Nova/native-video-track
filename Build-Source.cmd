@echo off
cd /d "%~dp0"
python tools\build_source.py all
set "NVT_BUILD_EXIT=%ERRORLEVEL%"
pause
exit /b %NVT_BUILD_EXIT%
