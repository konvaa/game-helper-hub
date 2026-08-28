import 'engine_input.dart';
import 'engine_result.dart';
import 'engine_runner.dart';

class LocalGameEngine {
  final EngineRunner _runner;
  LocalGameEngine(this._runner);

  EngineResult compute(EngineInput input) => _runner.run(input);
}