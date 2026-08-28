import 'dart:convert';
import 'package:http/http.dart' as http;

class EngineInputDto {
  final String generatedDir;
  final String skillKey;
  final int slvl;
  final int blvl;
  final String difficulty;
  final Map<String, int> overridesTotal;
  final Map<String, int> overridesBase;

  EngineInputDto({
    required this.generatedDir,
    required this.skillKey,
    required this.slvl,
    required this.blvl,
    required this.difficulty,
    required this.overridesTotal,
    required this.overridesBase,
  });

  Map<String, dynamic> toJson() => {
        'generated_dir': generatedDir,
        'skill_key': skillKey,
        'slvl': slvl,
        'blvl': blvl,
        'difficulty': difficulty,
        'overrides_total': overridesTotal,
        'overrides_base': overridesBase,
      };
}

class EngineResultDto {
  final String skillKey;
  final String monsterId;
  final String difficulty;
  final Map<String, dynamic> finalStats;
  final List<String> warnings;

  EngineResultDto({
    required this.skillKey,
    required this.monsterId,
    required this.difficulty,
    required this.finalStats,
    required this.warnings,
  });

  factory EngineResultDto.fromJson(Map<String, dynamic> json) {
    return EngineResultDto(
      skillKey: (json['skill_key'] as String?) ?? '',
      monsterId: (json['monster_id'] as String?) ?? '',
      difficulty: (json['difficulty'] as String?) ?? '',
      finalStats: (json['final'] as Map<String, dynamic>?) ?? const {},
      warnings: (json['warnings'] as List<dynamic>? ?? const [])
          .map((e) => e.toString())
          .toList(),
    );
  }
}

abstract class EngineClient {
  Future<EngineResultDto> compute(EngineInputDto input);
}

/// 1) Mock (UI poběží hned)
class MockEngineClient implements EngineClient {
  @override
  Future<EngineResultDto> compute(EngineInputDto input) async {
    // jen demo – nahradíš reálným voláním
    return EngineResultDto(
      skillKey: input.skillKey,
      monsterId: 'mock_monster',
      difficulty: input.difficulty,
      finalStats: {
        'hp': 123,
        'phys_min': 10,
        'phys_max': 20,
        'defense': 55,
        'attack_rating': 77,
        'count': input.skillKey == 'raise_skeleton' ? 8 : null,
      }..removeWhere((k, v) => v == null),
      warnings: const ['Mock result – wire real engine endpoint.'],
    );
  }
}

/// 2) HTTP klient (Python server / Firebase function / lokální endpoint)
class HttpEngineClient implements EngineClient {
  final Uri endpoint;

  HttpEngineClient(this.endpoint);

  @override
  Future<EngineResultDto> compute(EngineInputDto input) async {
    final resp = await http.post(
      endpoint,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(input.toJson()),
    );

    if (resp.statusCode < 200 || resp.statusCode >= 300) {
      throw Exception('Engine call failed: ${resp.statusCode} ${resp.body}');
    }

    final data = jsonDecode(resp.body) as Map<String, dynamic>;
    return EngineResultDto.fromJson(data);
  }
}
