import 'dart:typed_data';

import 'package:flutter/material.dart';
import '../models/app_info.dart';
import '../services/platform_channel_service.dart';

/// Widget displaying an individual app with lock toggle
class AppListTile extends StatefulWidget {
  final AppInfo app;
  final VoidCallback onToggleLock;

  const AppListTile({super.key, required this.app, required this.onToggleLock});

  @override
  State<AppListTile> createState() => _AppListTileState();
}

class _AppListTileState extends State<AppListTile> {
  static final Map<String, Uint8List?> _iconCache = <String, Uint8List?>{};
  static final PlatformChannelService _platformService =
      PlatformChannelService();

  Uint8List? _icon;
  bool _loadingIcon = false;

  @override
  void initState() {
    super.initState();
    _icon = widget.app.icon;
    _loadIconIfNeeded();
  }

  @override
  void didUpdateWidget(covariant AppListTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.app.packageName != widget.app.packageName) {
      _icon = widget.app.icon;
      _loadIconIfNeeded();
    }
  }

  Future<void> _loadIconIfNeeded() async {
    if (_icon != null) return;

    final packageName = widget.app.packageName;
    if (_iconCache.containsKey(packageName)) {
      if (!mounted) return;
      setState(() {
        _icon = _iconCache[packageName];
      });
      return;
    }

    if (_loadingIcon) return;
    _loadingIcon = true;
    final icon = await _platformService.getAppIcon(packageName);
    _iconCache[packageName] = icon;

    if (!mounted) return;
    setState(() {
      _icon = icon;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: _icon != null
          ? Image.memory(
              _icon!,
              width: 48,
              height: 48,
              fit: BoxFit.contain,
              errorBuilder: (context, error, stackTrace) {
                return const Icon(Icons.android, size: 48);
              },
            )
          : const Icon(Icons.android, size: 48),
      title: Text(
        widget.app.appName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        widget.app.packageName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.bodySmall,
      ),
      trailing: Switch(
        value: widget.app.isLocked,
        onChanged: (_) => widget.onToggleLock(),
        activeThumbColor: Theme.of(context).colorScheme.primary,
      ),
      onTap: widget.onToggleLock,
    );
  }
}
