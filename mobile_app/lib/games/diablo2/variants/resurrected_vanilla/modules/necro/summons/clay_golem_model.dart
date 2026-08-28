import '../../../../../../../core/engine/engine_runner.dart';
import '../../../../../../../core/engine/engine_result.dart';

class ClayGolemModel implements SummonModel {
  @override
  EngineResult compute(EngineContext ctx) {
    final slvl = ctx.inp.slvl;
    final diff = ctx.inp.difficulty.toLowerCase();

    final out = <String, dynamic>{
      'hp': ctx.base.hp,
      'phys_min': ctx.base.physMin,
      'phys_max': ctx.base.physMax,
      'defense': ctx.base.defense,
      'attack_rating': ctx.base.attackRating,
      'slvl': slvl,
      'difficulty': diff,
    };

    return EngineResult(
      skillKey: ctx.skillKey,
      monsterId: ctx.monsterId,
      difficulty: diff,
      finalStats: out,
      warnings: const [
        'ClayGolemModel: placeholder. Replace with ported verified formulas.'
      ],
    );
  }
}
