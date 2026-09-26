@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo === G134Office release build ===
call "%~dp0gradlew.bat" release --no-daemon
if errorlevel 1 (
    echo.
    echo Release build failed.
    exit /b 1
)

echo.
echo Done.
echo   ZIP ^(requires JDK 26^): build\distributions\G134Office-1.0.0.zip
echo   Bundled app:            build\package\G134Office\G134Office.exe
echo.
exit /b 0
