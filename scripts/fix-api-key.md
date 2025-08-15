# Fix Firebase API Key Issue

## Problem
Your `app/google-services.json` file contains an invalid API key (`AlzaSyAflyhi3H3C3Qacp17lsV680v0tgE7ZZQ8`), which is causing Firebase authentication failures.

## Solution

### Step 1: Get a Valid google-services.json File

1. **Go to Firebase Console**: https://console.firebase.google.com/
2. **Select your project** `gzingapp-bac3d` (or create a new one if it doesn't exist)
3. **Add an Android app** (if not already done):
   - Click "Add app" → Android icon
   - Package name: `com.example.gzingapp.debug` (for debug builds)
   - App nickname: `GzingApp Debug`
   - Debug signing certificate SHA-1: (optional, but recommended for Google Sign-In)

4. **Download google-services.json**:
   - Click "Download google-services.json"
   - Replace your current `app/google-services.json` file with this new file

### Step 2: Verify the Configuration

The new `google-services.json` should have:
- Valid `current_key` values in the `api_key` sections
- Correct `project_id`: `gzingapp-bac3d`
- Correct `package_name`: `com.example.gzingapp.debug`

### Step 3: Enable Required APIs

In Firebase Console, ensure these APIs are enabled:
1. **Authentication** → Sign-in method → Enable Anonymous and Email/Password
2. **Firestore Database** → Create database (start in test mode, then apply security rules)
3. **Storage** → Get started

### Step 4: Deploy Firebase Rules

After fixing the API key, run:
```bash
# Windows
scripts\deploy-firebase.bat

# Unix/Linux/macOS
chmod +x scripts/deploy-firebase.sh
./scripts/deploy-firebase.sh
```

### Step 5: Test the Fix

1. Clean and rebuild your Android project:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. Run the app and check if Firebase authentication works without the API key error.

## Troubleshooting

### If you still get API key errors:
1. Ensure the package name in `google-services.json` matches your app's package name
2. Check that the Firebase project is active and not deleted
3. Verify that Firebase APIs are enabled in Google Cloud Console
4. Try creating a fresh Firebase project if the issue persists

### If you need to create a new Firebase project:
1. Go to Firebase Console
2. Click "Create a project"
3. Follow the setup wizard
4. Download the new `google-services.json`
5. Update your project configuration

### Debug builds vs Release builds:
- Debug builds use package name: `com.example.gzingapp.debug`
- Release builds use package name: `com.example.gzingapp`
- Make sure you have configurations for both in your Firebase project
















