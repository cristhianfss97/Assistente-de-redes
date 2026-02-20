@echo off
setlocal
cd /d %~dp0\..

REM ---- JavaFX path detection ----
if not "%JAVAFX_HOME%"=="" (
  set FX=%JAVAFX_HOME%\lib
) else (
  if exist "C:\javafx-sdk-25.0.2\lib" (set FX=C:\javafx-sdk-25.0.2\lib) else (
    if exist "C:\javafx-sdk-21\lib" (set FX=C:\javafx-sdk-21\lib) else (
      echo ERRO: Defina a variavel JAVAFX_HOME apontando para a pasta do JavaFX (ex: C:\javafx-sdk-25.0.2)
      echo Exemplo: setx JAVAFX_HOME C:\javafx-sdk-25.0.2
      pause
      exit /b 1
    )
  )
)

rmdir /s /q build 2>nul
mkdir build\classes

echo Compilando...
javac --module-path "%FX%" --add-modules javafx.controls ^
  -encoding UTF-8 -d build\classes ^
  src\main\java\com\assistente\*.java
if errorlevel 1 (
  echo Falha ao compilar.
  pause
  exit /b 1
)

echo Copiando recursos...
xcopy /E /I /Y src\main\resources build\classes >nul

echo Executando...
java --module-path "%FX%" --add-modules javafx.controls ^
  -cp build\classes com.assistente.AssistenteRedesApp

endlocal
