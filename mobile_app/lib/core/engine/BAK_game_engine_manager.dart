import 'package:flutter/services.dart' show rootBundle;

import '../dataset/dataset.dart';
import 'engine_runner.dart';
import 'local_game_engine.dart';

import '../../games/diablo2/variants/resurrected_vanilla/registry/necro_registry.dart';

class GameEngineManager {
  GameEngineManager._();
  static final GameEngineManager I = GameEngineManager._();

  LocalGameEngine? _current;

  bool get isReady => _current != null;

  Future<void> initDiablo2ResurrectedVanilla() async {
    final json = await rootBundle.loadString(
      'assets/games/diablo2/resurrected_vanilla/dataset.json',
    );

    final ds = D2Dataset.fromJsonString(json);

    final runner = EngineRunner(
      ds: ds,
      registry: necroRegistry(),
    );

    _current = LocalGameEngine(runner);
  }

  LocalGameEngine get engine {
    final e = _current;
    if (e == null) {
      throw StateError("GameEngineManager not initialized.");
    }
    return e;
  }
}
