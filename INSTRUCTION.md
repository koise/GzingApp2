# Firebase CRUD Setup Guide for Your Android App

## 🔥 What I've Created for You

I've set up a complete Firebase CRUD system for your app with the following components:

### 1. **UserRepository** - Complete User Management
- ✅ Create new users
- ✅ Read user data by ID or email
- ✅ Update user information (partial or complete)
- ✅ Delete users
- ✅ Real-time user data listening
- ✅ Batch operations
- ✅ Search functionality

### 2. **EmergencyContactRepository** - Emergency Contacts Management
- ✅ Create emergency contacts
- ✅ Read all contacts for a user
- ✅ Update contact information
- ✅ Delete individual or all contacts
- ✅ Real-time contact updates

### 3. **Updated SessionManager** - Enhanced Session Management
- ✅ Firebase integration
- ✅ Automatic data synchronization
- ✅ Session validation and refresh
- ✅ User account management

### 4. **Updated ProfileActivity** - Real Firebase Integration
- ✅ Load user data from Firebase
- ✅ Save profile changes to Firebase
- ✅ Real-time emergency contacts
- ✅ Complete account deletion
- ✅ Proper error handling

## 🚀 Setup Instructions

### Step 1: Add Dependencies
Add the provided dependencies to your `app/build.gradle` file.

### Step 2: Firebase Project Setup
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or use existing one
3. Add your Android app to the project
4. Download `google-services.json` and place it in your `app/` directory

### Step 3: Enable Firebase Realtime Database
1. In Firebase Console, go to "Realtime Database"
2. Create database in test mode initially
3. Replace the rules with the improved security rules I provided

### Step 4: Replace Your Files
Replace your existing files with the updated versions:
- `SessionManager.kt`
- `ProfileActivity.kt`
- Add the new repository classes

## 📊 Database Structure
Your Firebase Realtime Database will be organized as:
```
your-app-name/
├── users/
│   └── {userId}/
│       ├── uid: string
│       ├── firstName: string
│       ├── lastName: string
│       ├── email: string
│       ├── phoneNumber: string
│       ├── username: string
│       ├── isAnonymous: boolean
│       └── createdAt: timestamp
└── emergency_contacts/
    └── {userId}/
        └── {contactId}/
            ├── id: string
            ├── name: string
            ├── phoneNumber: string
            └── relationship: string
```

## 🔐 Security Features
- Users can only access their own data
- Proper data validation rules
- Authentication required for all operations
- Emergency contacts are isolated per user

## 🎯 Key Features

### Real-time Updates
- Profile changes sync instantly
- Emergency contacts update in real-time
- Session data stays synchronized

### Error Handling
- Comprehensive try-catch blocks
- User-friendly error messages
- Graceful fallbacks

### Performance Optimizations
- Efficient data loading
- Minimal network requests
- Proper listener management

## 🔧 Usage Examples

### Creating a User
```kotlin
val user = User(
    uid = "user123",
    firstName = "John",
    lastName = "Doe",
    email = "john@example.com"
)

lifecycleScope.launch {
    val result = userRepository.createUser(user)
    result.fold(
        onSuccess = { /* Handle success */ },
        onFailure = { /* Handle error */ }
    )
}
```

### Updating User Profile
```kotlin
val updates = mapOf(
    "firstName" to "Jane",
    "phoneNumber" to "+1234567890"
)

lifecycleScope.launch {
    val success = sessionManager.updateUserData(updates)
    if (success) {
        // Profile updated successfully
    }
}
```

### Managing Emergency Contacts
```kotlin
val contact = EmergencyContact(
    id = UUID.randomUUID().toString(),
    name = "Emergency Contact",
    phoneNumber = "+911",
    relationship = "Hospital"
)

lifecycleScope.launch {
    val result = emergencyContactRepository.createEmergencyContact(userId, contact)
    // Handle result
}
```

## 🛡️ Security Rules Explained
The provided security rules ensure that:
- Only authenticated users can access data
- Users can only read/write their own information
- Data validation prevents malformed entries
- Emergency contacts are properly isolated

## 🚨 Important Notes
1. **Authentication**: Make sure to implement Firebase Authentication if you haven't already
2. **Error Handling**: All operations return `Result<T>` for proper error handling
3. **Listeners**: Always remove listeners in `onDestroy()` to prevent memory leaks
4. **Validation**: Client-side validation is included, but server-side rules provide additional security

## 🔄 Migration from SharedPreferences
Your existing emergency contacts stored in SharedPreferences will need to be migrated. You can add a migration method to copy data from SharedPreferences to Firebase on first login.

## 📱 Testing
1. Test with both online and offline scenarios
2. Verify real-time updates work correctly
3. Test error scenarios (network issues, invalid data)
4. Ensure proper cleanup when user logs out

This setup provides a robust, scalable, and secure foundation for your user profile and emergency contacts management!