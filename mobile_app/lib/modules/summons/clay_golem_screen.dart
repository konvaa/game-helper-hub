import 'package:flutter/material.dart';

class ClayGolemScreen extends StatelessWidget {
  const ClayGolemScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // Zatím čistá obrazovka. V dalším kroku sem dáme UI kalkulačky a napojení na engine.
    return Scaffold(
      appBar: AppBar(title: const Text('Clay Golem')),
      body: const Padding(
        padding: EdgeInsets.all(16),
        child: Text(
          'Clay Golem calculator UI will be here.\n\n'
          'Next step: inputs (difficulty, slvl/blvl, mastery override), compute, render result.',
        ),
      ),
    );
  }
}
