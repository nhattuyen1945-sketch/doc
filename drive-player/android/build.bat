@echo off
rem Build APK debug, chay unit test, copy APK ra DrivePlayer.apk
cd /d "%~dp0"
call gradlew.bat assembleDebug testDebugUnitTest || (echo BUILD LOI & pause & exit /b 1)
copy /y app\build\outputs\apk\debug\app-debug.apk DrivePlayer.apk >nul
echo Xong: %~dp0DrivePlayer.apk
echo Cai len dien thoai: "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r DrivePlayer.apk
pause
