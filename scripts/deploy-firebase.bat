@echo off
REM Firebase Deployment Script for Windows
REM This script deploys Firebase rules and indexes

echo =====================================
echo Firebase Deployment Script
echo =====================================

echo Checking Firebase CLI installation...
firebase --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Firebase CLI is not installed or not in PATH
    echo Please install Firebase CLI: npm install -g firebase-tools
    pause
    exit /b 1
)

echo Firebase CLI found.

REM Check if user is logged in
echo Checking Firebase authentication...
firebase projects:list >nul 2>&1
if errorlevel 1 (
    echo You are not logged in to Firebase.
    echo Please login first...
    firebase login
    if errorlevel 1 (
        echo ERROR: Failed to login to Firebase
        pause
        exit /b 1
    )
)

echo You are authenticated with Firebase.

REM Check if project is selected
echo Checking Firebase project selection...
firebase use --current >nul 2>&1
if errorlevel 1 (
    echo No Firebase project selected.
    echo Please select a project:
    firebase use --add
    if errorlevel 1 (
        echo ERROR: Failed to select Firebase project
        pause
        exit /b 1
    )
)

echo Current project:
firebase use --current

echo.
echo =====================================
echo Deploying Firebase Configuration
echo =====================================

REM Deploy Firestore rules
echo.
echo [1/3] Deploying Firestore security rules...
firebase deploy --only firestore:rules
if errorlevel 1 (
    echo ERROR: Failed to deploy Firestore rules
    pause
    exit /b 1
)
echo ✓ Firestore rules deployed successfully

REM Deploy Firestore indexes
echo.
echo [2/3] Deploying Firestore indexes...
firebase deploy --only firestore:indexes
if errorlevel 1 (
    echo ERROR: Failed to deploy Firestore indexes
    pause
    exit /b 1
)
echo ✓ Firestore indexes deployed successfully

REM Deploy Storage rules
echo.
echo [3/3] Deploying Storage security rules...
firebase deploy --only storage
if errorlevel 1 (
    echo ERROR: Failed to deploy Storage rules
    pause
    exit /b 1
)
echo ✓ Storage rules deployed successfully

echo.
echo =====================================
echo 🎉 Deployment Complete!
echo =====================================
echo All Firebase configurations have been deployed successfully.
echo.
echo Your Firestore rules and indexes are now active.
echo You can view them in the Firebase Console:
echo https://console.firebase.google.com/
echo.
pause
















