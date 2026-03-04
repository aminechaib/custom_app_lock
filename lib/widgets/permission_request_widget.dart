import 'package:flutter/material.dart';
import '../services/platform_channel_service.dart';
import 'guard_logo.dart';

/// Widget that requests required permissions from the user
class PermissionRequestWidget extends StatefulWidget {
  final VoidCallback onPermissionsGranted;

  const PermissionRequestWidget({
    super.key,
    required this.onPermissionsGranted,
  });

  @override
  State<PermissionRequestWidget> createState() =>
      _PermissionRequestWidgetState();
}

class _PermissionRequestWidgetState extends State<PermissionRequestWidget> {
  final PlatformChannelService _platformService = PlatformChannelService();

  bool _isCheckingPermissions = true;
  bool _hasUsageStats = false;
  bool _hasOverlay = false;
  bool _hasAccessibility = false;

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    setState(() {
      _isCheckingPermissions = true;
    });

    _hasUsageStats = await _platformService.hasUsageStatsPermission();
    _hasOverlay = await _platformService.hasOverlayPermission();
    _hasAccessibility = await _platformService.isAccessibilityServiceEnabled();

    setState(() {
      _isCheckingPermissions = false;
    });

    // If all permissions granted, call the callback
    if (_hasUsageStats && _hasOverlay && _hasAccessibility) {
      widget.onPermissionsGranted();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isCheckingPermissions) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Checking permissions...'),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const GuardLogo(size: 84),
          const SizedBox(height: 24),
          Text(
            'App Locker Setup',
            style: Theme.of(
              context,
            ).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            'To protect your apps, we need the following permissions:',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 24),

          // Usage Stats Permission
          _buildPermissionCard(
            icon: Icons.analytics,
            title: 'Usage Access',
            description: 'Required to detect which app is currently open',
            isGranted: _hasUsageStats,
            onRequest: _requestUsageStatsPermission,
          ),
          const SizedBox(height: 16),

          // Overlay Permission
          _buildPermissionCard(
            icon: Icons.layers,
            title: 'Display Over Other Apps',
            description:
                'Required to show the lock screen when a protected app opens',
            isGranted: _hasOverlay,
            onRequest: _requestOverlayPermission,
          ),
          const SizedBox(height: 16),

          // Accessibility Service
          _buildPermissionCard(
            icon: Icons.accessibility_new,
            title: 'Accessibility Service',
            description:
                'Required to monitor app launches and show lock screen',
            isGranted: _hasAccessibility,
            onRequest: _requestAccessibilityService,
          ),

          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _checkPermissions,
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
              child: const Text('Check Permissions Again'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionCard({
    required IconData icon,
    required String title,
    required String description,
    required bool isGranted,
    required VoidCallback onRequest,
  }) {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isGranted
                    ? Colors.green.withValues(alpha: 0.1)
                    : Colors.orange.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                icon,
                size: 32,
                color: isGranted ? Colors.green : Colors.orange,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            if (isGranted)
              const Icon(Icons.check_circle, color: Colors.green)
            else
              ElevatedButton(onPressed: onRequest, child: const Text('Grant')),
          ],
        ),
      ),
    );
  }

  Future<void> _requestUsageStatsPermission() async {
    await _platformService.requestUsageStatsPermission();
    _checkPermissions();
  }

  Future<void> _requestOverlayPermission() async {
    await _platformService.requestOverlayPermission();
    _checkPermissions();
  }

  Future<void> _requestAccessibilityService() async {
    await _platformService.requestAccessibilityService();
    _checkPermissions();
  }
}
