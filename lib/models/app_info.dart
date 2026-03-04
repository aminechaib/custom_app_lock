import 'dart:typed_data';

/// Model class representing an installed application
class AppInfo {
  final String packageName;
  final String appName;
  final Uint8List? icon;
  bool isLocked;

  AppInfo({
    required this.packageName,
    required this.appName,
    this.icon,
    this.isLocked = false,
  });

  /// Create from JSON map (for platform channel communication)
  factory AppInfo.fromMap(Map<dynamic, dynamic> map) {
    return AppInfo(
      packageName: map['packageName'] as String? ?? '',
      appName: map['appName'] as String? ?? '',
      icon: map['icon'] as Uint8List?,
      isLocked: map['isLocked'] as bool? ?? false,
    );
  }

  /// Convert to JSON map
  Map<String, dynamic> toMap() {
    return {
      'packageName': packageName,
      'appName': appName,
      'isLocked': isLocked,
    };
  }

  @override
  String toString() =>
      'AppInfo(packageName: $packageName, appName: $appName, isLocked: $isLocked)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is AppInfo && other.packageName == packageName;
  }

  @override
  int get hashCode => packageName.hashCode;
}
