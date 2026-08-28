import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;

import '../core/dataset/dataset.dart';
import '../core/engine/engine_runner.dart';
import '../core/engine/local_game_engine.dart';

import '../games/diablo2/variants/resurrected_vanilla/registry/necro_registry.dart';

/// Variant IDs (pro UI dropdown)
class GameVariantId {
  static const d2rVanilla = 'diablo2.resurrected_vanilla';
  // future:
  // static const d2ClassicVanilla = 'diablo2.classic_vanilla';
  // static const d2rModded = 'diablo2.resurrected_modded';
}

class GameEngineManager extends ChangeNotifier {
  GameEngineManager._();
  static final GameEngineManager I = GameEngineManager._();

  LocalGameEngine? _engine;
  String? _currentVariantId;
  bool _loading = false;
  String? _error;

  LocalGameEngine? get engine => _engine;
  String? get currentVariantId => _currentVariantId;
  bool get isReady => _engine != null;
  bool get isLoading => _loading;
  String? get error => _error;

  /// Variant list pro dropdown (zatím 1, ale UI bude ready)
  List<String> get availableVariants => const [
        GameVariantId.d2rVanilla,
      ];

  Future<void> selectVariant(String variantId) async {
    if (_loading) return;
    if (_currentVariantId == variantId && _engine != null) return;

    _loading = true;
    _error = null;
    notifyListeners();

    try {
      // Tady mapuješ variantId -> asset path + registry
      if (variantId == GameVariantId.d2rVanilla) {
        final json = await rootBundle.loadString(
          'assets/games/diablo2/resurrected_vanilla/dataset.json',
        );
        final ds = D2Dataset.fromJsonString(json);

        final runner = EngineRunner(
          ds: ds,
          registry: necroRegistry(),
        );

        _engine = LocalGameEngine(runner);
        _currentVariantId = variantId;
      } else {
        throw StateError('Unknown variantId: $variantId');
      }
    } catch (e) {
      _engine = null;
      _currentVariantId = null;
      _error = e.toString();
    } finally {
      _loading = false;
      notifyListeners();
    }
  }
}
