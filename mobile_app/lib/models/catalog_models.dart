class Catalog {
  final String schema;
  final List<GameDef> games;

  Catalog({required this.schema, required this.games});

  factory Catalog.fromJson(Map<String, dynamic> json) {
    final gamesJson = (json['games'] as List<dynamic>? ?? []);
    return Catalog(
      schema: (json['schema'] ?? '').toString(),
      games: gamesJson.map((e) => GameDef.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }

  GameDef? findGame(String gameId) {
    for (final g in games) {
      if (g.id == gameId) return g;
    }
    return null;
  }
}

class GameDef {
  final String id;
  final String title;
  final List<VariantDef> variants;

  GameDef({required this.id, required this.title, required this.variants});

  factory GameDef.fromJson(Map<String, dynamic> json) {
    final variantsJson = (json['variants'] as List<dynamic>? ?? []);
    return GameDef(
      id: (json['id'] ?? '').toString(),
      title: (json['title'] ?? '').toString(),
      variants: variantsJson.map((e) => VariantDef.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }

  VariantDef? findVariant(String variantId) {
    for (final v in variants) {
      if (v.id == variantId) return v;
    }
    return null;
  }
}

class VariantDef {
  final String id;
  final String title;
  final List<ModuleDef> modules;

  VariantDef({required this.id, required this.title, required this.modules});

  factory VariantDef.fromJson(Map<String, dynamic> json) {
    final modulesJson = (json['modules'] as List<dynamic>? ?? []);
    return VariantDef(
      id: (json['id'] ?? '').toString(),
      title: (json['title'] ?? '').toString(),
      modules: modulesJson.map((e) => ModuleDef.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}

class ModuleDef {
  final String id;
  final String title;
  final bool enabled;

  ModuleDef({required this.id, required this.title, required this.enabled});

  factory ModuleDef.fromJson(Map<String, dynamic> json) {
    return ModuleDef(
      id: (json['id'] ?? '').toString(),
      title: (json['title'] ?? '').toString(),
      enabled: (json['enabled'] ?? false) == true,
    );
  }
}
