import 'package:shared_preferences/shared_preferences.dart';

/// Service for persisting locked apps list using SharedPreferences
class AppStorageService {
  static const String _lockedAppsKey = 'locked_apps';

  late SharedPreferences _prefs;
  bool _isInitialized = false;

  /// Initialize the storage service
  Future<void> init() async {
    if (!_isInitialized) {
      _prefs = await SharedPreferences.getInstance();
      _isInitialized = true;
    }
  }

  /// Get list of locked app package names
  Future<List<String>> getLockedApps() async {
    await init();
    return _prefs.getStringList(_lockedAppsKey) ?? [];
  }

  /// Save list of locked app package names
  Future<bool> saveLockedApps(List<String> packageNames) async {
    await init();
    return await _prefs.setStringList(_lockedAppsKey, packageNames);
  }

  /// Add an app to the locked list
  Future<bool> addLockedApp(String packageName) async {
    final lockedApps = await getLockedApps();
    if (!lockedApps.contains(packageName)) {
      lockedApps.add(packageName);
      return await saveLockedApps(lockedApps);
    }
    return true;
  }

  /// Remove an app from the locked list
  Future<bool> removeLockedApp(String packageName) async {
    final lockedApps = await getLockedApps();
    lockedApps.remove(packageName);
    return await saveLockedApps(lockedApps);
  }

  /// Check if an app is locked
  Future<bool> isAppLocked(String packageName) async {
    final lockedApps = await getLockedApps();
    return lockedApps.contains(packageName);
  }

  /// Toggle lock status for an app
  Future<bool> toggleAppLock(String packageName) async {
    final isLocked = await isAppLocked(packageName);
    if (isLocked) {
      return await removeLockedApp(packageName);
    } else {
      return await addLockedApp(packageName);
    }
  }

  /// Clear all locked apps
  Future<bool> clearLockedApps() async {
    await init();
    return await _prefs.remove(_lockedAppsKey);
  }
}
