import 'package:flutter/material.dart';

import 'screens/summon_list_screen.dart';
import 'services/feature_flags.dart';
import 'services/registry_loader.dart';
import 'services/game_engine_manager.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // zatím neinitujeme pevně variantu — necháme to na UI dropdownu
  // GameEngineManager.I.init(...) se zavolá až po výběru varianty

  runApp(const GameHelperApp());
}

class GameHelperApp extends StatelessWidget {
  const GameHelperApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Game Helper Hub',
      theme: ThemeData(useMaterial3: true),
      home: SummonListScreen(
        loader: RegistryLoader(),
        flags: defaultFeatureFlags,
      ),
    );
  }
}