class EngineResult {
  final String skillKey;
  final String monsterId;
  final String difficulty;
  final Map<String, dynamic> finalStats;
  final List<String> warnings;

  EngineResult({
    required this.skillKey,
    required this.monsterId,
    required this.difficulty,
    Map<String, dynamic>? finalStats,
    List<String>? warnings,
  })  : finalStats = finalStats ?? {},
        warnings = warnings ?? [];
}
