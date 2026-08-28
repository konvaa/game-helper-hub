import '../../../../../core/engine/engine_runner.dart';
import '../modules/necro/summons/clay_golem_model.dart';
import '../modules/necro/summons/raise_skeleton_model.dart';
import '../modules/necro/summons/raise_skeletal_mage_model.dart';

Map<String, SummonModel Function()> necroRegistry() => {
      'clay_golem': () => ClayGolemModel(),
      'raise_skeleton': () => RaiseSkeletonModel(),
      'raise_skeletal_mage': () => RaiseSkeletalMageModel(),
    };
