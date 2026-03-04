import 'package:flutter/material.dart';
import '../models/app_info.dart';
import '../services/app_storage_service.dart';
import '../services/platform_channel_service.dart';
import '../widgets/app_list_tile.dart';
import '../widgets/guard_logo.dart';
import '../widgets/permission_request_widget.dart';

/// Main screen displaying list of installed apps with lock toggles
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final PlatformChannelService _platformService = PlatformChannelService();
  final AppStorageService _storageService = AppStorageService();

  List<AppInfo> _apps = [];
  List<String> _lockedApps = [];
  bool _isLoading = true;
  bool _hasPermissions = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    if (!mounted) return;
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      // Check all required permissions
      final hasUsageStats = await _platformService.hasUsageStatsPermission();
      final hasOverlay = await _platformService.hasOverlayPermission();
      final hasAccessibility = await _platformService
          .isAccessibilityServiceEnabled();

      _hasPermissions = hasUsageStats && hasOverlay && hasAccessibility;

      if (!_hasPermissions) {
        if (!mounted) return;
        setState(() {
          _isLoading = false;
        });
        return;
      }

      // Load locked apps from storage
      _lockedApps = await _storageService.getLockedApps();

      // Get installed apps
      final installedApps = await _platformService.getInstalledApps();

      // Update isLocked status for each app
      for (var app in installedApps) {
        app.isLocked = _lockedApps.contains(app.packageName);
      }

      // Sort apps: locked apps first, then alphabetically
      installedApps.sort((a, b) {
        if (a.isLocked && !b.isLocked) return -1;
        if (!a.isLocked && b.isLocked) return 1;
        return a.appName.toLowerCase().compareTo(b.appName.toLowerCase());
      });

      // Start the app locker service
      final serviceStarted = await _platformService.startAppLockerService();
      final nativeUpdated = await _platformService.updateLockedApps(
        _lockedApps,
      );
      if (!serviceStarted || !nativeUpdated) {
        throw Exception('Failed to initialize native app lock service');
      }

      if (!mounted) return;
      setState(() {
        _apps = installedApps;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  Future<void> _toggleAppLock(AppInfo app) async {
    final previousLockStatus = app.isLocked;
    final previousLockedApps = List<String>.from(_lockedApps);
    final newLockStatus = !app.isLocked;

    // Update local state immediately for responsive UI
    setState(() {
      app.isLocked = newLockStatus;
      if (newLockStatus) {
        _lockedApps.add(app.packageName);
      } else {
        _lockedApps.remove(app.packageName);
      }
      // Re-sort the list
      _apps.sort((a, b) {
        if (a.isLocked && !b.isLocked) return -1;
        if (!a.isLocked && b.isLocked) return 1;
        return a.appName.toLowerCase().compareTo(b.appName.toLowerCase());
      });
    });

    try {
      // Persist the change
      final persisted = await _storageService.toggleAppLock(app.packageName);
      final nativeUpdated = await _platformService.updateLockedApps(
        _lockedApps,
      );
      if (!persisted || !nativeUpdated) {
        throw Exception('Failed to save lock state');
      }
    } catch (_) {
      if (!mounted) return;
      setState(() {
        app.isLocked = previousLockStatus;
        _lockedApps = previousLockedApps;
        _apps.sort((a, b) {
          if (a.isLocked && !b.isLocked) return -1;
          if (!a.isLocked && b.isLocked) return 1;
          return a.appName.toLowerCase().compareTo(b.appName.toLowerCase());
        });
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Could not update lock state. Please retry.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            GuardLogo(size: 30),
            SizedBox(width: 10),
            Text('App Locker Guard'),
          ],
        ),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          if (_hasPermissions && !_isLoading)
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _initialize,
              tooltip: 'Refresh',
            ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Loading installed apps...'),
          ],
        ),
      );
    }

    if (!_hasPermissions) {
      return PermissionRequestWidget(onPermissionsGranted: _initialize);
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text('Error: $_error'),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: _initialize, child: const Text('Retry')),
          ],
        ),
      );
    }

    if (_apps.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.apps, size: 64, color: Colors.grey),
            SizedBox(height: 16),
            Text('No apps found'),
          ],
        ),
      );
    }

    return Column(
      children: [
        // Header with stats
        Container(
          padding: const EdgeInsets.all(16),
          color: Theme.of(context).colorScheme.primaryContainer,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '${_apps.length} apps installed',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Text(
                '${_lockedApps.length} locked',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
        // App list
        Expanded(
          child: ListView.builder(
            itemCount: _apps.length,
            itemBuilder: (context, index) {
              return AppListTile(
                key: ValueKey(_apps[index].packageName),
                app: _apps[index],
                onToggleLock: () => _toggleAppLock(_apps[index]),
              );
            },
          ),
        ),
      ],
    );
  }
}
