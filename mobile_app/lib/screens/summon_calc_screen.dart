import 'package:flutter/material.dart';

import '../models/summon_ui_registry.dart';
import '../core/engine/engine_input.dart';
import '../core/engine/engine_result.dart';
import '../services/game_engine_manager.dart';

enum Difficulty { normal, nightmare, hell }

String difficultyToEngine(Difficulty d) {
  switch (d) {
    case Difficulty.normal:
      return 'normal';
    case Difficulty.nightmare:
      return 'nightmare';
    case Difficulty.hell:
      return 'hell';
  }
}

class SummonCalcScreen extends StatefulWidget {
  final RegistryItem item;

  const SummonCalcScreen({
    super.key,
    required this.item,
  });

  @override
  State<SummonCalcScreen> createState() => _SummonCalcScreenState();
}

class _SummonCalcScreenState extends State<SummonCalcScreen> {
  // Vanilla rules (future: move to variant config)
  static const int _slvlMax = 60;
  static const int _baseCap = 20;

  Difficulty _difficulty = Difficulty.hell;
  int _slvl = 20;
  int _blvl = 20;

  // overrides: skillKey -> (total, base)
  final Map<String, int> _ovTotal = {};
  final Map<String, int> _ovBase = {};

  bool _loading = false;
  String? _error;
  EngineResult? _result;

  @override
  void initState() {
    super.initState();

    // init override fields to something sane
    for (final o in widget.item.overrides) {
      if (o.total) _ovTotal[o.skillKey] = 1;
      if (o.base) _ovBase[o.skillKey] = 1;
    }

    // enforce constraints on initial values
    _setBlvl(_blvl);
    _setSlvl(_slvl);
  }

  // --- SLVL/BLVL rules ---
  void _setSlvl(int newSlvl) {
    newSlvl = newSlvl.clamp(0, _slvlMax);
    var newBlvl = _blvl;

    // If total goes below base, base must follow down.
    if (newSlvl < newBlvl) newBlvl = newSlvl;

    setState(() {
      _slvl = newSlvl;
      _blvl = newBlvl;
    });
  }

  void _setBlvl(int newBlvl) {
    newBlvl = newBlvl.clamp(0, _baseCap);
    var newSlvl = _slvl;

    // base can raise total if needed
    if (newBlvl > newSlvl) newSlvl = newBlvl;

    setState(() {
      _blvl = newBlvl;
      _slvl = newSlvl;
    });
  }

  // --- Override rules: base<=total, base cap 20, total up to 60 ---
  void _setOverrideTotal(String skillKey, int value) {
    value = value.clamp(0, _slvlMax);
    var base = _ovBase[skillKey] ?? 0;

    if (value < base) base = value;

    setState(() {
      _ovTotal[skillKey] = value;
      _ovBase[skillKey] = base;
    });
  }

  void _setOverrideBase(String skillKey, int value) {
    value = value.clamp(0, _baseCap);
    var total = _ovTotal[skillKey] ?? 0;

    if (value > total) total = value;

    setState(() {
      _ovBase[skillKey] = value;
      _ovTotal[skillKey] = total;
    });
  }

  Future<void> _compute() async {
    final gm = GameEngineManager.I;

    if (!gm.isReady || gm.engine == null) {
      setState(() {
        _error = 'Engine not ready. Go back and pick a variant.';
        _result = null;
      });
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });

    try {
      await Future<void>.delayed(Duration.zero);

      final input = EngineInput(
        skillKey: widget.item.skillKey,
        slvl: _slvl,
        blvl: _blvl,
        difficulty: difficultyToEngine(_difficulty),
        overridesTotal: Map<String, int>.from(_ovTotal),
        overridesBase: Map<String, int>.from(_ovBase),
      );

      final res = gm.engine!.compute(input);

      setState(() => _result = res);
    } catch (e) {
      // Friendlier message for placeholder dataset
      final msg = e.toString();
      if (msg.contains('Dataset is empty (placeholder)')) {
        setState(() {
          _error =
              'Dataset is placeholder.\n\nReplace:\nassets/games/diablo2/resurrected_vanilla/dataset.json\nwith your generated dataset JSON.';
        });
      } else {
        setState(() => _error = msg);
      }
    } finally {
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;

    return Scaffold(
      appBar: AppBar(title: Text(item.title)),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          _InputsCard(
            difficulty: _difficulty,
            slvl: _slvl,
            blvl: _blvl,
            slvlMax: _slvlMax,
            blvlMax: _baseCap,
            onDifficulty: (v) => setState(() => _difficulty = v),
            onSlvl: _setSlvl,
            onBlvl: _setBlvl,
          ),
          const SizedBox(height: 12),
          if (item.overrides.isNotEmpty)
            _OverridesCard(
              overrides: item.overrides,
              getTotal: (k) => _ovTotal[k] ?? 0,
              getBase: (k) => _ovBase[k] ?? 0,
              onTotalChanged: _setOverrideTotal,
              onBaseChanged: _setOverrideBase,
              totalMax: _slvlMax,
              baseMax: _baseCap,
            ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _loading ? null : _compute,
            child: _loading
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator())
                : const Text('Compute'),
          ),
          const SizedBox(height: 12),
          if (_error != null) _ErrorCard(error: _error!),
          if (_result != null) _ResultCard(result: _result!, outputs: item.outputs),
        ],
      ),
    );
  }
}

class _InputsCard extends StatelessWidget {
  final Difficulty difficulty;
  final int slvl;
  final int blvl;
  final int slvlMax;
  final int blvlMax;

  final ValueChanged<Difficulty> onDifficulty;
  final ValueChanged<int> onSlvl;
  final ValueChanged<int> onBlvl;

  const _InputsCard({
    required this.difficulty,
    required this.slvl,
    required this.blvl,
    required this.slvlMax,
    required this.blvlMax,
    required this.onDifficulty,
    required this.onSlvl,
    required this.onBlvl,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Row(
              children: [
                const Expanded(child: Text('Difficulty')),
                DropdownButton<Difficulty>(
                  value: difficulty,
                  items: const [
                    DropdownMenuItem(value: Difficulty.normal, child: Text('Normal')),
                    DropdownMenuItem(value: Difficulty.nightmare, child: Text('Nightmare')),
                    DropdownMenuItem(value: Difficulty.hell, child: Text('Hell')),
                  ],
                  onChanged: (v) => onDifficulty(v ?? Difficulty.hell),
                )
              ],
            ),
            const Divider(),
            _IntFieldRow(
              label: 'SLVL (total)',
              value: slvl,
              min: 0,
              max: slvlMax,
              onChanged: onSlvl,
            ),
            _IntFieldRow(
              label: 'BLVL (base)',
              value: blvl,
              min: 0,
              max: blvlMax,
              onChanged: onBlvl,
            ),
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Rule: base ≤ total, base cap $blvlMax',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OverridesCard extends StatelessWidget {
  final List<RegistryOverride> overrides;
  final int Function(String skillKey) getTotal;
  final int Function(String skillKey) getBase;
  final void Function(String skillKey, int value) onTotalChanged;
  final void Function(String skillKey, int value) onBaseChanged;
  final int totalMax;
  final int baseMax;

  const _OverridesCard({
    required this.overrides,
    required this.getTotal,
    required this.getBase,
    required this.onTotalChanged,
    required this.onBaseChanged,
    required this.totalMax,
    required this.baseMax,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Overrides', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...overrides.map((o) {
              final total = getTotal(o.skillKey);
              final base = getBase(o.skillKey);

              return Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(o.title, style: Theme.of(context).textTheme.bodyLarge),
                    const SizedBox(height: 6),
                    if (o.total)
                      _IntFieldRow(
                        label: 'Total lvl',
                        value: total,
                        min: 0,
                        max: totalMax,
                        onChanged: (v) => onTotalChanged(o.skillKey, v),
                      ),
                    if (o.base)
                      _IntFieldRow(
                        label: 'Base lvl',
                        value: base,
                        min: 0,
                        max: baseMax,
                        onChanged: (v) => onBaseChanged(o.skillKey, v),
                      ),
                    if (o.total && o.base)
                      Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Text(
                          'Rule: base ≤ total, base cap $baseMax',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                  ],
                ),
              );
            }),
          ],
        ),
      ),
    );
  }
}

class _ResultCard extends StatelessWidget {
  final EngineResult result;
  final List<String> outputs;

  const _ResultCard({required this.result, required this.outputs});

  @override
  Widget build(BuildContext context) {
    final entries = result.finalStats.entries.toList()
      ..sort((a, b) => a.key.compareTo(b.key));

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Result', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 6),
            Text('Monster: ${result.monsterId}'),
            Text('Difficulty: ${result.difficulty}'),
            const Divider(),
            ...entries.map(
              (e) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(
                  children: [
                    Expanded(child: Text(e.key)),
                    Text('${e.value}'),
                  ],
                ),
              ),
            ),
            if (result.warnings.isNotEmpty) ...[
              const Divider(),
              Text('Warnings', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 6),
              ...result.warnings.map((w) => Text('• $w')),
            ],
          ],
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  final String error;
  const _ErrorCard({required this.error});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Text('Error: $error'),
      ),
    );
  }
}

class _IntFieldRow extends StatelessWidget {
  final String label;
  final int value;
  final int min;
  final int max;
  final ValueChanged<int> onChanged;

  const _IntFieldRow({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: Text(label)),
        IconButton(
          onPressed: value > min ? () => onChanged(value - 1) : null,
          icon: const Icon(Icons.remove),
        ),
        SizedBox(
          width: 56,
          child: Text(
            '$value',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ),
        IconButton(
          onPressed: value < max ? () => onChanged(value + 1) : null,
          icon: const Icon(Icons.add),
        ),
      ],
    );
  }
}
