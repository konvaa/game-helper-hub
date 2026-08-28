import 'package:flutter/services.dart';
import '../models/summon_ui_registry.dart';

class RegistryLoader {
  final String assetPath;

  RegistryLoader({this.assetPath = 'assets/registry/summon_ui_registry.json'});

  Future<SummonUiRegistry> load() async {
    final raw = await rootBundle.loadString(assetPath);
    return SummonUiRegistry.fromJsonString(raw);
  }
}
