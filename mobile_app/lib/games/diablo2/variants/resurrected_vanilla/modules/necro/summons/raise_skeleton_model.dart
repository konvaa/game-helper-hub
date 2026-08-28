import '../../../../../../../core/engine/engine_runner.dart';
import '../../../../../../../core/engine/engine_result.dart';

class RaiseSkeletonModel implements SummonModel {
  @override
  EngineResult compute(EngineContext ctx) {
    final diff = ctx.inp.difficulty.toLowerCase();

    final out = <String, dynamic>{
      'hp': ctx.base.hp,
      'phys_min': ctx.base.physMin,
      'phys_max': ctx.base.physMax,
      'defense': ctx.base.defense,
      'attack_rating': ctx.base.attackRating,
      // TODO: count + temp dmg + dmg% formula (ported from Python)
    };

    return EngineResult(
      skillKey: ctx.skillKey,
      monsterId: ctx.monsterId,
      difficulty: diff,
      finalStats: out,
      warnings: const [
        'RaiseSkeletonModel: placeholder. Port verified RS formula (temp dmg + % multip + temp dmg).'
      ],
    );
  }
}
