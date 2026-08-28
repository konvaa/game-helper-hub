import 'package:flutter/material.dart';

import 'summons_registry.dart';

class SummonsHomeScreen extends StatefulWidget {
  final String gameId;
  final String variantId;

  const SummonsHomeScreen({
    super.key,
    required this.gameId,
    required this.variantId,
  });

  @override
  State<SummonsHomeScreen> createState() => _SummonsHomeScreenState();
}

class _SummonsHomeScreenState extends State<SummonsHomeScreen> {
  String? _selectedClassId;

  @override
  void initState() {
    super.initState();
    final classes = SummonsRegistry.classesForVariant(
      gameId: widget.gameId,
      variantId: widget.variantId,
    );
    _selectedClassId = classes.isNotEmpty ? classes.first.id : null;
  }

  @override
  Widget build(BuildContext context) {
    final classes = SummonsRegistry.classesForVariant(
      gameId: widget.gameId,
      variantId: widget.variantId,
    );

    final classId = _selectedClassId;
    final summons = (classId == null)
        ? const <SummonEntry>[]
        : SummonsRegistry.summonsFor(
            gameId: widget.gameId,
            variantId: widget.variantId,
            classId: classId,
          );

    return Scaffold(
      appBar: AppBar(title: const Text('Summons')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: _ClassPicker(
              classes: classes,
              selectedClassId: _selectedClassId,
              onChanged: (v) => setState(() => _selectedClassId = v),
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: summons.isEmpty
                ? _EmptyState(classId: _selectedClassId)
                : ListView.separated(
                    padding: const EdgeInsets.all(12),
                    itemCount: summons.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final s = summons[index];
                      return Opacity(
                        opacity: s.enabled ? 1.0 : 0.55,
                        child: Card(
                          child: ListTile(
                            title: Text(s.title),
                            subtitle: Text(s.id),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                if (!s.enabled) const _SoonBadge(),
                                const SizedBox(width: 8),
                                const Icon(Icons.chevron_right),
                              ],
                            ),
                            onTap: s.enabled
                                ? () => _openSummon(context, s)
                                : () => _showSoon(context, s.title),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  void _openSummon(BuildContext context, SummonEntry s) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => s.builder(context)),
    );
  }

  void _showSoon(BuildContext context, String title) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$title — coming soon')),
    );
  }
}

class _ClassPicker extends StatelessWidget {
  final List<SummonClass> classes;
  final String? selectedClassId;
  final ValueChanged<String?> onChanged;

  const _ClassPicker({
    required this.classes,
    required this.selectedClassId,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          children: [
            const Icon(Icons.person_outline),
            const SizedBox(width: 10),
            Expanded(
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  isExpanded: true,
                  value: selectedClassId,
                  items: classes
                      .map(
                        (c) => DropdownMenuItem<String>(
                          value: c.id,
                          child: Text(c.title),
                        ),
                      )
                      .toList(),
                  onChanged: classes.isEmpty ? null : onChanged,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  final String? classId;
  const _EmptyState({required this.classId});

  @override
  Widget build(BuildContext context) {
    final msg = (classId == null)
        ? 'No classes available for this variant.'
        : 'No summons available yet for this class.';
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(
          msg,
          textAlign: TextAlign.center,
        ),
      ),
    );
  }
}

class _SoonBadge extends StatelessWidget {
  const _SoonBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
      ),
      child: Text('Soon', style: Theme.of(context).textTheme.labelSmall),
    );
  }
}
