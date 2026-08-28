import 'dart:convert';

class SummonUiRegistry {
  final RegistryMeta meta;
  final List<RegistrySection> sections;

  SummonUiRegistry({required this.meta, required this.sections});

  factory SummonUiRegistry.fromJson(Map<String, dynamic> json) {
    return SummonUiRegistry(
      meta: RegistryMeta.fromJson(json['meta'] as Map<String, dynamic>),
      sections: (json['sections'] as List<dynamic>)
          .map((e) => RegistrySection.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  static SummonUiRegistry fromJsonString(String s) =>
      SummonUiRegistry.fromJson(jsonDecode(s) as Map<String, dynamic>);
}

class RegistryMeta {
  final String schema;
  final String? notes;

  RegistryMeta({required this.schema, this.notes});

  factory RegistryMeta.fromJson(Map<String, dynamic> json) => RegistryMeta(
        schema: (json['schema'] as String?) ?? '',
        notes: json['notes'] as String?,
      );
}

class RegistrySection {
  final String id;
  final String title;
  final bool premium;
  final String? featureFlag;
  final List<RegistryItem> items;

  RegistrySection({
    required this.id,
    required this.title,
    required this.premium,
    required this.items,
    this.featureFlag,
  });

  factory RegistrySection.fromJson(Map<String, dynamic> json) => RegistrySection(
        id: (json['id'] as String?) ?? '',
        title: (json['title'] as String?) ?? '',
        premium: (json['premium'] as bool?) ?? false,
        featureFlag: json['feature_flag'] as String?,
        items: (json['items'] as List<dynamic>? ?? const [])
            .map((e) => RegistryItem.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class RegistryItem {
  final String skillKey;
  final String title;
  final RegistryInputs inputs;
  final List<RegistryOverride> overrides;
  final List<String> outputs;

  RegistryItem({
    required this.skillKey,
    required this.title,
    required this.inputs,
    required this.overrides,
    required this.outputs,
  });

  factory RegistryItem.fromJson(Map<String, dynamic> json) => RegistryItem(
        skillKey: (json['skill_key'] as String?) ?? '',
        title: (json['title'] as String?) ?? '',
        inputs: RegistryInputs.fromJson(
            (json['inputs'] as Map<String, dynamic>?) ?? const {}),
        overrides: (json['overrides'] as List<dynamic>? ?? const [])
            .map((e) => RegistryOverride.fromJson(e as Map<String, dynamic>))
            .toList(),
        outputs: (json['outputs'] as List<dynamic>? ?? const [])
            .map((e) => e.toString())
            .toList(),
      );
}

class RegistryInputs {
  final bool difficulty;
  final bool slvl;
  final bool blvl;

  RegistryInputs({
    required this.difficulty,
    required this.slvl,
    required this.blvl,
  });

  factory RegistryInputs.fromJson(Map<String, dynamic> json) => RegistryInputs(
        difficulty: (json['difficulty'] as bool?) ?? true,
        slvl: (json['slvl'] as bool?) ?? true,
        blvl: (json['blvl'] as bool?) ?? true,
      );
}

class RegistryOverride {
  final String skillKey;
  final String title;
  final bool total;
  final bool base;

  RegistryOverride({
    required this.skillKey,
    required this.title,
    required this.total,
    required this.base,
  });

  factory RegistryOverride.fromJson(Map<String, dynamic> json) =>
      RegistryOverride(
        skillKey: (json['skill_key'] as String?) ?? '',
        title: (json['title'] as String?) ?? '',
        total: (json['total'] as bool?) ?? true,
        base: (json['base'] as bool?) ?? true,
      );
}
