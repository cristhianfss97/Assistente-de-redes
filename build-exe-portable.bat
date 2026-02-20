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

rmdir /s /q dist 2>nul
mkdir dist

echo Gerando EXE portatil (app-image)...
jpackage ^
  --type app-image ^
  --name AssistenteRedes ^
  --input . ^
  --main-jar AssistenteRedes.jar ^
  --java-options "--module-path %FX% --add-modules javafx.controls" ^
  --dest dist

echo.
echo OK! dist\AssistenteRedes\AssistenteRedes.exe
echo Dica: compacte e distribua a pasta dist\AssistenteRedes\
pause
endlocal
