import '../dataset/dataset.dart';
import '../expr/tokenizer.dart';
import '../expr/parser.dart';
import '../expr/eval.dart';
import '../expr/ast.dart';

import 'engine_input.dart';
import 'engine_result.dart';

class BaseStats {
  final double hp;
  final double physMin;
  final double physMax;
  final double defense;
  final double attackRating;

  BaseStats({
    required this.hp,
    required this.physMin,
    required this.physMax,
    required this.defense,
    required this.attackRating,
  });
}

class EngineContext {
  final D2Dataset ds;
  final EngineInput inp;

  final String skillKey;
  final Map<String, dynamic> skillRec;

  final String monsterId;
  final Map<String, dynamic> monsterRec;

  final BaseStats base;

  EngineContext({
    required this.ds,
    required this.inp,
    required this.skillKey,
    required this.skillRec,
    required this.monsterId,
    required this.monsterRec,
    required this.base,
  });

  double evalExpr(String expr, {required int lvl, required Map<String, num> vars}) {
    final toks = Tokenizer(expr).tokenize();
    final ast = Parser(toks).parse();
    return evalNode(ast as ExprNode, EvalContext(lvl: lvl, vars: vars));
  }
}

abstract class SummonModel {
  EngineResult compute(EngineContext ctx);
}

/// Minimal extract for base stats from monster record.
/// Upravíš mapování podle svého generated dataset formátu.
BaseStats extractBaseStats(Map<String, dynamic> mon, String difficulty) {
  double readNum(String key, [double def = 0]) {
    final v = mon[key];
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v) ?? def;
    return def;
  }

  return BaseStats(
    hp: readNum('hp'),
    physMin: readNum('phys_min'),
    physMax: readNum('phys_max'),
    defense: readNum('defense'),
    attackRating: readNum('attack_rating'),
  );
}

/// Default summoned monster id picker.
/// Expects skillRec['summoned_monster'] (nebo fallback).
String pickSummonedMonsterId(Map<String, dynamic> skillRec) {
  final v = skillRec['summoned_monster'] ?? skillRec['summon'] ?? skillRec['pettype'];
  if (v is String && v.isNotEmpty) return v;
  throw StateError('Cannot resolve summoned monster id from skill record');
}

class EngineRunner {
  final D2Dataset ds;
  final Map<String, SummonModel Function()> registry;

  EngineRunner({required this.ds, required this.registry});

  EngineResult run(EngineInput inp) {
    final sk = normalizeKey(inp.skillKey);
    final skill = ds.skill(sk);

    final monsterId = pickSummonedMonsterId(skill);
    final monster = ds.monster(monsterId);

    final base = extractBaseStats(monster, inp.difficulty.toLowerCase());

    final ctx = EngineContext(
      ds: ds,
      inp: inp,
      skillKey: sk,
      skillRec: skill,
      monsterId: monsterId,
      monsterRec: monster,
      base: base,
    );

    final factory = registry[sk];
    if (factory == null) {
      return EngineResult(
        skillKey: sk,
        monsterId: monsterId,
        difficulty: inp.difficulty,
        finalStats: {
          'hp': base.hp,
          'phys_min': base.physMin,
          'phys_max': base.physMax,
          'defense': base.defense,
          'attack_rating': base.attackRating,
        },
        warnings: ["No model implemented for skill '$sk' yet."],
      );
    }

    return factory().compute(ctx);
  }
}
