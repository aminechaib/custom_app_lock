import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ac_applock/main.dart';

void main() {
  testWidgets('App boots and renders Home scaffold', (WidgetTester tester) async {
    await tester.pumpWidget(const AppLockerApp());
    await tester.pump();

    expect(find.byType(MaterialApp), findsOneWidget);
    expect(find.text('App Locker Guard'), findsOneWidget);
  });
}
