import 'package:flutter/material.dart';

class GuardLogo extends StatelessWidget {
  final double size;

  const GuardLogo({super.key, this.size = 44});

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/images/guard_logo.png',
      width: size,
      height: size,
      fit: BoxFit.contain,
      errorBuilder: (context, error, stackTrace) {
        return Icon(Icons.shield, size: size, color: Colors.cyan.shade700);
      },
    );
  }
}
