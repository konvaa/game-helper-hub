import 'package:flutter/material.dart';

import '../models/summon_ui_registry.dart';
import '../services/feature_flags.dart';
import '../services/registry_loader.dart';
import '../services/game_engine_manager.dart';
import 'summon_calc_screen.dart';

class SummonListScreen extends StatefulWidget {
  final RegistryLoader loader;
  final FeatureFlags flags;

  const SummonListScreen({
    super.key,
    required this.loader,
    required this.flags,
  });

  @override
  State<SummonListScreen> createState() => _SummonListScreenState();
}

class _SummonListScreenState extends State<SummonListScreen> {
  late Future<SummonUiRegistry> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.loader.load();

    // Auto-select první dostupnou variantu (zatím 1, ale UI je ready na future)
    final gm = GameEngineManager.I;
    if (gm.availableVariants.isNotEmpty) {
      gm.selectVariant(gm.availableVariants.first);
    }
  }

  @override
  Widget build(BuildContext context) {
    final gm = GameEngineManager.I;

    return Scaffold(
      appBar: AppBar(title: const Text('Summon calculators')),
      body: AnimatedBuilder(
        animation: gm,
        builder: (context, _) {
          return Column(
            children: [
              _VariantBar(
                currentVariantId: gm.currentVariantId,
                variants: gm.availableVariants,
                isLoading: gm.isLoading,
                error: gm.error,
                onSelected: (v) => gm.selectVariant(v),
              ),
              Expanded(
                child: FutureBuilder<SummonUiRegistry>(
                  future: _future,
                  builder: (context, snap) {
                    if (snap.connectionState != ConnectionState.done) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    if (snap.hasError) {
                      return _ErrorView(
                        error: snap.error.toString(),
                        onRetry: () => setState(() => _future = widget.loader.load()),
                      );
                    }

                    final reg = snap.data!;
                    final engineReady = gm.isReady;

                    return ListView.builder(
                      itemCount: reg.sections.length,
                      itemBuilder: (context, i) {
                        final section = reg.sections[i];

                        // Feature-flag gating (premium / from-gear etc.)
                        final enabled = widget.flags.isEnabled(section.featureFlag);

                        return _SectionCard(
                          section: section,
                          enabled: enabled,
                          engineReady: engineReady,
                          onOpenItem: (item) {
                            Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => SummonCalcScreen(item: item),
                              ),
                            );
                          },
                        );
                      },
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _VariantBar extends StatelessWidget {
  final String? currentVariantId;
  final List<String> variants;
  final bool isLoading;
  final String? error;
  final ValueChanged<String> onSelected;

  const _VariantBar({
    required this.currentVariantId,
    required this.variants,
    required this.isLoading,
    required this.error,
    required this.onSelected,
  });

  String _prettyName(String variantId) {
    // Teď jen hezčí label pro 1 variantu.
    // Až přidáš další, můžeš sem dát mapu.
    switch (variantId) {
      case GameVariantId.d2rVanilla:
        return 'Diablo II Resurrected – Vanilla';
      default:
        return variantId;
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasVariants = variants.isNotEmpty;
    final selected = currentVariantId ?? (hasVariants ? variants.first : null);

    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      child: Column(
        children: [
          Row(
            children: [
              const Text('Variant:'),
              const SizedBox(width: 12),
              Expanded(
                child: DropdownButton<String>(
                  isExpanded: true,
                  value: selected,
                  items: variants
                      .map((v) => DropdownMenuItem(
                            value: v,
                            child: Text(_prettyName(v)),
                          ))
                      .toList(),
                  onChanged: hasVariants
                      ? (v) {
                          if (v != null) onSelected(v);
                        }
                      : null,
                ),
              ),
              const SizedBox(width: 12),
              if (isLoading)
                const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(),
                ),
            ],
          ),
          if (error != null) ...[
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Engine error: $error',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  final RegistrySection section;
  final bool enabled;
  final bool engineReady;
  final void Function(RegistryItem item) onOpenItem;

  const _SectionCard({
    required this.section,
    required this.enabled,
    required this.engineReady,
    required this.onOpenItem,
  });

  @override
  Widget build(BuildContext context) {
    final lockedByPremium = section.premium && !enabled;
    final lockedByEngine = !engineReady && !lockedByPremium;

    return Card(
      margin: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    section.title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                if (section.premium)
                  Chip(label: Text(lockedByPremium ? 'Premium (locked)' : 'Premium')),
                if (lockedByEngine)
                  const Padding(
                    padding: EdgeInsets.only(left: 8),
                    child: Chip(label: Text('Engine not ready')),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            if (lockedByPremium)
              const Text('This section is premium. Unlock to access gear summons.')
            else if (lockedByEngine)
              const Text('Engine is still loading or failed. Pick a variant above.')
            else if (section.items.isEmpty)
              const Text('No items yet.')
            else
              ...section.items.map(
                (item) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(item.title),
                  subtitle: Text(item.skillKey),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => onOpenItem(item),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String error;
  final VoidCallback onRetry;

  const _ErrorView({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load registry:\n$error'),
            const SizedBox(height: 12),
            ElevatedButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
