@echo off
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 goto :missing
where gradle >nul 2>nul
if errorlevel 1 goto :missing
call gradle --version | findstr /C:"Gradle 8.11.1" >nul
if errorlevel 1 goto :missing
echo Required: JDK 17, Android platform 35, build-tools 35.0.0 and accepted SDK licenses.
call gradle --no-daemon :app:assembleDebug
if errorlevel 1 (
  echo Build failed. No usable APK is asserted. Keep the full build log.
  pause
  exit /b 1
)
echo Expected output: app/build/outputs/apk/debug/app-debug.apk
pause
exit /b 0
:missing
echo Install JDK 17 and Gradle 8.11.1, and configure JAVA_HOME, PATH and Android SDK.
echo This source package does not include a built APK or Gradle wrapper.
pause
exit /b 1
