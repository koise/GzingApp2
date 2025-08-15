#!/bin/bash
# Firebase Deployment Script for Unix/Linux/macOS
# This script deploys Firebase rules and indexes

set -e  # Exit on any error

echo "====================================="
echo "Firebase Deployment Script"
echo "====================================="

# Check if Firebase CLI is installed
echo "Checking Firebase CLI installation..."
if ! command -v firebase &> /dev/null; then
    echo "ERROR: Firebase CLI is not installed or not in PATH"
    echo "Please install Firebase CLI: npm install -g firebase-tools"
    exit 1
fi

echo "Firebase CLI found: $(firebase --version)"

# Check if user is logged in
echo "Checking Firebase authentication..."
if ! firebase projects:list &> /dev/null; then
    echo "You are not logged in to Firebase."
    echo "Please login first..."
    firebase login
fi

echo "✓ You are authenticated with Firebase."

# Check if project is selected
echo "Checking Firebase project selection..."
if ! firebase use --current &> /dev/null; then
    echo "No Firebase project selected."
    echo "Please select a project:"
    firebase use --add
fi

echo "Current project: $(firebase use --current)"

echo ""
echo "====================================="
echo "Deploying Firebase Configuration"
echo "====================================="

# Deploy Firestore rules
echo ""
echo "[1/3] Deploying Firestore security rules..."
firebase deploy --only firestore:rules
echo "✓ Firestore rules deployed successfully"

# Deploy Firestore indexes
echo ""
echo "[2/3] Deploying Firestore indexes..."
firebase deploy --only firestore:indexes
echo "✓ Firestore indexes deployed successfully"

# Deploy Storage rules
echo ""
echo "[3/3] Deploying Storage security rules..."
firebase deploy --only storage
echo "✓ Storage rules deployed successfully"

echo ""
echo "====================================="
echo "🎉 Deployment Complete!"
echo "====================================="
echo "All Firebase configurations have been deployed successfully."
echo ""
echo "Your Firestore rules and indexes are now active."
echo "You can view them in the Firebase Console:"
echo "https://console.firebase.google.com/"
echo ""
















