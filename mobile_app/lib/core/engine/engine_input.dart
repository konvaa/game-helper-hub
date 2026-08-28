class EngineInput {
  final String skillKey;
  final int slvl;
  final int blvl;
  final String difficulty; // "normal" | "nightmare" | "hell"
  final Map<String, int> overridesTotal;
  final Map<String, int> overridesBase;

  EngineInput({
    required this.skillKey,
    required this.slvl,
    required this.blvl,
    required this.difficulty,
    Map<String, int>? overridesTotal,
    Map<String, int>? overridesBase,
  })  : overridesTotal = overridesTotal ?? const {},
        overridesBase = overridesBase ?? const {};
}
