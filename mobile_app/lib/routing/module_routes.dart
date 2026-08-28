import 'package:flutter/material.dart';

import '../modules/summons/summons_home_screen.dart';

typedef ModuleRouteBuilder = Widget Function(
  BuildContext context, {
  required String gameId,
  required String variantId,
});

class ModuleRoutes {
  /// Central registry: moduleId -> screen builder
  static final Map<String, ModuleRouteBuilder> routes = {
    'summons': (context, {required gameId, required variantId}) =>
        SummonsHomeScreen(gameId: gameId, variantId: variantId),
  };

  static bool canOpen(String moduleId) => routes.containsKey(moduleId);

  static void open(
    BuildContext context, {
    required String moduleId,
    required String gameId,
    required String variantId,
  }) {
    final builder = routes[moduleId];
    if (builder == null) {
      _showMissingModuleDialog(context, moduleId);
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (ctx) => builder(ctx, gameId: gameId, variantId: variantId),
      ),
    );
  }

  static void _showMissingModuleDialog(BuildContext context, String moduleId) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Module not implemented'),
        content: Text("No route registered for module '$moduleId'."),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('OK')),
        ],
      ),
    );
  }
}
