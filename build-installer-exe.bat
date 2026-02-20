@echo off
setlocal
cd /d %~dp0\..

call scripts\build-jar.bat
if errorlevel 1 exit /b 1

if not "%JAVAFX_HOME%"=="" (
  set FX=%JAVAFX_HOME%\lib
) else (
  if exist "C:\javafx-sdk-25.0.2\lib" (set FX=C:\javafx-sdk-25.0.2\lib) else (
    if exist "C:\javafx-sdk-21\lib" (set FX=C:\javafx-sdk-21\lib) else (
      echo ERRO: Defina JAVAFX_HOME (ex: setx JAVAFX_HOME C:\javafx-sdk-25.0.2)
      pause
      exit /b 1
    )
  )
)

rmdir /s /q release 2>nul
mkdir release

echo Gerando instalador (Setup.exe)...
echo OBS: Pode exigir WiX Toolset instalado no Windows.

jpackage ^
  --type exe ^
  --name AssistenteRedes ^
  --input . ^
  --main-jar AssistenteRedes.jar ^
  --java-options "--module-path %FX% --add-modules javafx.controls" ^
  --win-shortcut ^
  --win-menu ^
  --dest release

echo.
echo Procure um arquivo .exe dentro de release\\ (nome pode variar).
pause
endlocal
