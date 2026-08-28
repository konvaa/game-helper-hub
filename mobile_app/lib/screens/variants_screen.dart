import 'package:flutter/material.dart';
import '../models/catalog_models.dart';
import 'modules_screen.dart';

class VariantsScreen extends StatelessWidget {
  final Catalog catalog;
  final String gameId;

  const VariantsScreen({super.key, required this.catalog, required this.gameId});

  @override
  Widget build(BuildContext context) {
    final game = catalog.findGame(gameId);

    if (game == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Variants')),
        body: const Center(child: Text('Game not found')),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(game.title)),
      body: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: game.variants.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final v = game.variants[index];
          return Card(
            child: ListTile(
              title: Text(v.title),
              subtitle: Text(v.id),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => ModulesScreen(
                      catalog: catalog,
                      gameId: game.id,
                      variantId: v.id,
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
