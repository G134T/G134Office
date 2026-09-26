@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "ISS=%INNO_SETUP_HOME%\ISCC.exe"
if not exist "%ISS%" set "ISS=%ProgramFiles%\Inno Setup 7\ISCC.exe"
if not exist "%ISS%" set "ISS=%ProgramFiles(x86)%\Inno Setup 7\ISCC.exe"
if not exist "%ISS%" set "ISS=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not exist "%ISS%" set "ISS=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISS%" set "ISS=F:\Program Files\Inno Setup 7\ISCC.exe"
if not exist "%ISS%" (
  echo ISCC.exe not found:
  echo Install Inno Setup 6 or 7, or set INNO_SETUP_HOME.
  exit /b 1
)
if not exist "build\package\G134Office\G134Office.exe" (
  echo Run build-release.cmd first to create the app-image.
  exit /b 1
)
echo Using %ISS%
"%ISS%" "G134Office.iss"
if errorlevel 1 (
  echo Inno Setup failed
  exit /b 1
)
echo OK build\installer\G134Office-Setup-1.0.0.exe
exit /b 0
