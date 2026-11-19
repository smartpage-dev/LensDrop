#!/bin/sh
set -eu
cd "$(dirname "$0")"
command -v java >/dev/null || { echo 'Install JDK 17 and set JAVA_HOME.'; exit 1; }
command -v gradle >/dev/null || { echo 'Install Gradle 8.11.1 and add its bin directory to PATH. No Gradle wrapper is bundled.'; exit 1; }
gradle --version | grep -q 'Gradle 8.11.1' || { echo 'This project requires Gradle 8.11.1 for the documented AGP 8.9.1 build.'; exit 1; }
echo 'Required: Android SDK platform 35, build-tools 35.0.0, accepted SDK licenses, network access to Google/Maven repositories.'
gradle --no-daemon :app:assembleDebug
echo 'Expected output: app/build/outputs/apk/debug/app-debug.apk. This debug APK is for controlled tests only.'
