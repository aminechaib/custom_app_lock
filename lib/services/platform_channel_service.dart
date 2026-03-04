import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../models/app_info.dart';

/// Service for platform channel communication with native Android code
class PlatformChannelService {
  static const MethodChannel _channel = MethodChannel(
    'com.example.ac_applock/app_locker',
  );

  /// Get list of all installed user apps
  Future<List<AppInfo>> getInstalledApps() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod(
        'getInstalledApps',
      );
      return result
          .map((app) => AppInfo.fromMap(app as Map<dynamic, dynamic>))
          .toList();
    } on PlatformException catch (e) {
      debugPrint('Failed to get installed apps: ${e.message}');
      return [];
    }
  }

  /// Get app icon as bytes
  Future<Uint8List?> getAppIcon(String packageName) async {
    try {
      final result = await _channel.invokeMethod('getAppIcon', {
        'packageName': packageName,
      });
      return result as Uint8List?;
    } on PlatformException catch (e) {
      debugPrint('Failed to get app icon: ${e.message}');
      return null;
    }
  }

  /// Check if usage stats permission is granted
  Future<bool> hasUsageStatsPermission() async {
    try {
      final bool result = await _channel.invokeMethod(
        'hasUsageStatsPermission',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to check usage stats permission: ${e.message}');
      return false;
    }
  }

  /// Request usage stats permission (opens system settings)
  Future<bool> requestUsageStatsPermission() async {
    try {
      final bool result = await _channel.invokeMethod(
        'requestUsageStatsPermission',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to request usage stats permission: ${e.message}');
      return false;
    }
  }

  /// Check if overlay permission is granted
  Future<bool> hasOverlayPermission() async {
    try {
      final bool result = await _channel.invokeMethod('hasOverlayPermission');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to check overlay permission: ${e.message}');
      return false;
    }
  }

  /// Request overlay permission (opens system settings)
  Future<bool> requestOverlayPermission() async {
    try {
      final bool result = await _channel.invokeMethod(
        'requestOverlayPermission',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to request overlay permission: ${e.message}');
      return false;
    }
  }

  /// Check if accessibility service is enabled
  Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final bool result = await _channel.invokeMethod(
        'isAccessibilityServiceEnabled',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to check accessibility service: ${e.message}');
      return false;
    }
  }

  /// Request accessibility service (opens system settings)
  Future<bool> requestAccessibilityService() async {
    try {
      final bool result = await _channel.invokeMethod(
        'requestAccessibilityService',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to request accessibility service: ${e.message}');
      return false;
    }
  }

  /// Start the app locker background service
  Future<bool> startAppLockerService() async {
    try {
      final bool result = await _channel.invokeMethod('startAppLockerService');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to start app locker service: ${e.message}');
      return false;
    }
  }

  /// Stop the app locker background service
  Future<bool> stopAppLockerService() async {
    try {
      final bool result = await _channel.invokeMethod('stopAppLockerService');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to stop app locker service: ${e.message}');
      return false;
    }
  }

  /// Update the list of locked apps in the native service
  Future<bool> updateLockedApps(List<String> packageNames) async {
    try {
      final bool result = await _channel.invokeMethod('updateLockedApps', {
        'packageNames': packageNames,
      });
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to update locked apps: ${e.message}');
      return false;
    }
  }

  /// Check if biometric authentication is available
  Future<bool> isBiometricAvailable() async {
    try {
      final bool result = await _channel.invokeMethod('isBiometricAvailable');
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to check biometric availability: ${e.message}');
      return false;
    }
  }

  /// Authenticate with biometric (fingerprint only)
  Future<bool> authenticateWithBiometric() async {
    try {
      final bool result = await _channel.invokeMethod(
        'authenticateWithBiometric',
      );
      return result;
    } on PlatformException catch (e) {
      debugPrint('Failed to authenticate with biometric: ${e.message}');
      return false;
    }
  }

  /// Unlock an app after successful authentication
  Future<void> unlockApp(String packageName) async {
    try {
      await _channel.invokeMethod('unlockApp', {'packageName': packageName});
    } on PlatformException catch (e) {
      debugPrint('Failed to unlock app: ${e.message}');
    }
  }
}
