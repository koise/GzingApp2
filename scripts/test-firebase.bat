@echo off
REM Firebase Testing Script for Windows
REM This script starts Firebase emulators for local testing

echo =====================================
echo Firebase Emulator Testing Script
echo =====================================

echo Checking Firebase CLI installation...
firebase --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Firebase CLI is not installed
    echo Please run setup-firebase.bat first
    pause
    exit /b 1
)

echo Starting Firebase Emulators...
echo.
echo This will start:
echo - Firestore Emulator (port 8080)
echo - Auth Emulator (port 9099)
echo - Storage Emulator (port 9199)
echo - Emulator UI (port 4000)
echo.
echo Press Ctrl+C to stop the emulators
echo.

REM Start emulators
firebase emulators:start

pause
















