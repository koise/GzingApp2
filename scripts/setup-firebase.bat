@echo off
REM Firebase Initial Setup Script for Windows
REM This script helps set up Firebase CLI and project

echo =====================================
echo Firebase Setup Script for GzingApp
echo =====================================

echo Checking Node.js installation...
node --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Node.js is not installed
    echo Please install Node.js from https://nodejs.org/
    pause
    exit /b 1
)

echo Node.js found: 
node --version

echo.
echo Checking Firebase CLI installation...
firebase --version >nul 2>&1
if errorlevel 1 (
    echo Firebase CLI not found. Installing...
    npm install -g firebase-tools
    if errorlevel 1 (
        echo ERROR: Failed to install Firebase CLI
        pause
        exit /b 1
    )
    echo ✓ Firebase CLI installed successfully
) else (
    echo ✓ Firebase CLI already installed:
    firebase --version
)

echo.
echo =====================================
echo Firebase Authentication
echo =====================================
echo Please login to your Firebase account...
firebase login
if errorlevel 1 (
    echo ERROR: Failed to login to Firebase
    pause
    exit /b 1
)

echo ✓ Successfully logged in to Firebase

echo.
echo =====================================
echo Project Setup
echo =====================================
echo Initializing Firebase in current directory...

REM Initialize Firebase project
firebase init
if errorlevel 1 (
    echo ERROR: Failed to initialize Firebase project
    pause
    exit /b 1
)

echo.
echo =====================================
echo Setup Complete!
echo =====================================
echo Firebase has been set up successfully.
echo.
echo Next steps:
echo 1. Update your app/google-services.json with the correct API key
echo 2. Run deploy-firebase.bat to deploy rules and indexes
echo 3. Test your app with the new configuration
echo.
pause
















