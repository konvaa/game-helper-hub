import 'package:flutter/material.dart';
import '../models/catalog_models.dart';
import '../routing/module_routes.dart';

class ModulesScreen extends StatelessWidget {
  final Catalog catalog;
  final String gameId;
  final String variantId;

  const ModulesScreen({
    super.key,
    required this.catalog,
    required this.gameId,
    required this.variantId,
  });

  @override
  Widget build(BuildContext context) {
    final game = catalog.findGame(gameId);
    final variant = game?.findVariant(variantId);

    if (game == null || variant == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Modules')),
        body: const Center(child: Text('Variant not found')),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(variant.title)),
      body: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: variant.modules.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final m = variant.modules[index];

          final canOpen = m.enabled && ModuleRoutes.canOpen(m.id);

          return Opacity(
            opacity: m.enabled ? 1.0 : 0.55,
            child: Card(
              child: ListTile(
                title: Text(m.title),
                subtitle: Text(m.id),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (!m.enabled) const _SoonBadge(),
                    const SizedBox(width: 8),
                    const Icon(Icons.chevron_right),
                  ],
                ),
                onTap: canOpen
                    ? () => ModuleRoutes.open(
                          context,
                          moduleId: m.id,
                          gameId: gameId,
                          variantId: variantId,
                        )
                    : () => _handleUnavailable(context, m),
              ),
            ),
          );
        },
      ),
    );
  }

  void _handleUnavailable(BuildContext context, ModuleDef m) {
    if (!m.enabled) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${m.title} — coming soon')),
      );
      return;
    }

    // enabled in catalog but not registered in routes => developer signal
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Route missing'),
        content: Text("Module '${m.id}' is enabled in catalog, but no route is registered."),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK')),
        ],
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
