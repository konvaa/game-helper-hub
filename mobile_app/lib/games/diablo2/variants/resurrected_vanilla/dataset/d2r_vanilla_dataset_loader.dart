import 'package:flutter/services.dart' show rootBundle;
import '../../../../../core/dataset/dataset.dart';

class D2RVanillaDatasetLoader {
  Future<D2Dataset> load() async {
    final json = await rootBundle.loadString(
      'assets/games/diablo2/resurrected_vanilla/dataset.json',
    );
    return D2Dataset.fromJsonString(json);
  }
}
