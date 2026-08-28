import 'dart:convert';

class D2Dataset {
  final Map<String, dynamic> _root;

  D2Dataset(this._root);

  factory D2Dataset.fromJsonString(String s) {
    final obj = jsonDecode(s);
    if (obj is! Map<String, dynamic>) {
      throw ArgumentError('Dataset JSON must be an object');
    }
    return D2Dataset(obj);
  }

  Map<String, dynamic> get skills => (_root['skills'] as Map?)?.cast<String, dynamic>() ?? {};
  Map<String, dynamic> get monsters => (_root['monsters'] as Map?)?.cast<String, dynamic>() ?? {};

  void assertNotPlaceholder() {
    if (skills.isEmpty || monsters.isEmpty) {
      final meta = (_root['meta'] as Map?)?.cast<String, dynamic>() ?? {};
      final notes = meta['notes']?.toString() ?? '';
      throw StateError(
        'Dataset is empty (placeholder). Replace assets/games/.../dataset.json with generated data. '
        '${notes.isNotEmpty ? "Notes: $notes" : ""}',
      );
    }
  }

  Map<String, dynamic> skill(String key) {
    assertNotPlaceholder();

    final raw = key.trim();
    final norm = normalizeKey(raw);

    final a = skills[norm];
    if (a is Map) return a.cast<String, dynamic>();

    final b = skills[raw];
    if (b is Map) return b.cast<String, dynamic>();

    for (final entry in skills.entries) {
      final k = entry.key;
      if (k is String && normalizeKey(k) == norm) {
        final v = entry.value;
        if (v is Map) return v.cast<String, dynamic>();
      }
    }

    throw StateError("Skill '$norm' not found in dataset");
  }

  Map<String, dynamic> monster(String id) {
    assertNotPlaceholder();

    final raw = id.trim();
    final norm = normalizeKey(raw);

    final a = monsters[raw];
    if (a is Map) return a.cast<String, dynamic>();

    final b = monsters[norm];
    if (b is Map) return b.cast<String, dynamic>();

    for (final entry in monsters.entries) {
      final k = entry.key;
      if (k is String && normalizeKey(k) == norm) {
        final v = entry.value;
        if (v is Map) return v.cast<String, dynamic>();
      }
    }

    throw StateError("Monster '$raw' not found in dataset");
  }
}

String normalizeKey(String s) {
  return s
      .trim()
      .toLowerCase()
      .replaceAll(RegExp(r'\s+'), '_')
      .replaceAll('-', '_');
}
