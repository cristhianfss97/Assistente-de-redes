@echo off
setlocal
cd /d %~dp0\..

call scripts\build-exe-portable.bat
if errorlevel 1 exit /b 1

call scripts\pack-portable-zip.bat
endlocal
