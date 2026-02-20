@echo off
setlocal
cd /d %~dp0\..

if not exist dist\AssistenteRedes\AssistenteRedes.exe (
  echo ERRO: dist\AssistenteRedes\AssistenteRedes.exe nao encontrado.
  echo Rode primeiro: scripts\build-exe-portable.bat
  pause
  exit /b 1
)

rmdir /s /q release 2>nul
mkdir release

echo Criando ZIP de distribuicao...
powershell -NoProfile -Command ^
  "Compress-Archive -Force -Path 'dist\AssistenteRedes\*' -DestinationPath 'release\AssistenteRedes-Portable-Windows.zip'"

echo.
echo OK: release\AssistenteRedes-Portable-Windows.zip
echo Envie esse ZIP para outras maquinas (nao renomeie o EXE).
pause
endlocal
