# App Locker - Comprehensive Plan

## Project Overview
Create an Android App Locker application using Flutter that:
- Lists all user-installed third-party apps
- Allows toggling lock on/off for each app
- Runs a background service to monitor foreground apps
- Intercepts locked apps and shows system-native fingerprint prompt
- Unlocks app on successful fingerprint authentication

## Information Gathered

### Current State
- Flutter project with `local_auth` package already added in pubspec.yaml
- Basic MainActivity.kt with Flutter embedding
- AndroidManifest.xml needs permissions and service declarations
- Main.dart is a default Flutter counter app template

### Key Requirements Analysis
1. **App Selection UI**: List installed apps with toggle switches
2. **Background Service**: Monitor foreground apps using UsageStatsManager or AccessibilityService
3. **Intercept and Lock**: Use SYSTEM_ALERT_WINDOW to overlay lock screen
4. **Biometric Prompt**: Use BiometricPrompt API (fingerprint only, no fallback)
5. **Unlock on Success**: Remove overlay and grant access

### Dependencies Required
- `local_auth` - for biometric authentication in Flutter
- `shared_preferences` - for storing locked apps list
- Native Android: UsageStatsManager, AccessibilityService, BiometricPrompt

## Plan

### Phase 1: Flutter UI Layer
1. Update pubspec.yaml with required dependencies
2. Create app_list_model.dart - Model for app information
3. Create app_lock_service.dart - Platform channel handler
4. Create home_screen.dart - Main screen with app list
5. Create app_list_tile.dart - Individual app item widget
6. Update main.dart - App entry point with proper routing

### Phase 2: Android Native Layer
1. Create AppLockerService.kt - Background foreground service
2. Create AppLockerAccessibilityService.kt - Accessibility service for monitoring
3. Create LockScreenActivity.kt - Overlay activity for lock screen
4. Create MethodChannelHandler.kt - Flutter platform channel handler
5. Update AndroidManifest.xml with permissions and service declarations

### Phase 3: Integration
1. Set up platform channel communication
2. Implement app listing using PackageManager
3. Implement lock/unlock state persistence
4. Connect biometric authentication flow
5. Test full integration

## Dependent Files to be Edited/Created

### New Flutter Files
- `lib/models/app_info.dart` - App model
- `lib/services/app_locker_service.dart` - Platform channel service
- `lib/services/package_info_service.dart` - Get installed apps
- `lib/screens/home_screen.dart` - Main app list screen
- `lib/widgets/app_list_tile.dart` - App item widget
- `lib/utils/permissions_helper.dart` - Permission handling

### New Android Files
- `android/app/src/main/kotlin/.../AppLockerService.kt` - Background service
- `android/app/src/main/kotlin/.../AppLockerAccessibilityService.kt` - Accessibility service
- `android/app/src/main/kotlin/.../LockScreenActivity.kt` - Lock overlay
- `android/app/src/main/kotlin/.../MethodChannelHandler.kt` - Platform channel
- `android/app/src/main/res/xml/accessibility_service_config.xml` - Accessibility config

### Files to Modify
- `pubspec.yaml` - Add dependencies
- `android/app/src/main/AndroidManifest.xml` - Add permissions
- `android/app/build.gradle.kts` - Add dependencies
- `lib/main.dart` - Update entry point

## Followup Steps
1. Run `flutter pub get` to fetch dependencies
2. Build debug APK to verify shell project compiles
3. Test permissions flow on device
4. Test biometric authentication
5. Test app monitoring and locking functionality

