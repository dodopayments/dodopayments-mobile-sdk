import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:dodopayments_checkout_example/main.dart';

void main() {
  testWidgets('shows the price page', (tester) async {
    await tester.pumpWidget(const ExampleApp());

    expect(find.text('Pro License'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Buy Now'), findsOneWidget);
  });
}
