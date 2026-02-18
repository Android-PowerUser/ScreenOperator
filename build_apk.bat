@echo off
echo Starting APK build...
call gradlew.bat assembleDebug > build_apk_log.txt 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Build FAILED. See build_apk_log.txt
    exit /b %ERRORLEVEL%
) else (
    echo Build SUCCESS!
    exit /b 0
)
