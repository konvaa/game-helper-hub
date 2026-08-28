import 'package:flutter/material.dart';
import '../models/catalog_models.dart';
import 'variants_screen.dart';

class GamesScreen extends StatelessWidget {
  final Catalog catalog;
  const GamesScreen({super.key, required this.catalog});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Select Game')),
      body: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: catalog.games.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final g = catalog.games[index];
          return Card(
            child: ListTile(
              title: Text(g.title),
              subtitle: Text(g.id),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => VariantsScreen(catalog: catalog, gameId: g.id),
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
