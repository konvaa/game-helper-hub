import 'package:flutter/material.dart';

import 'clay_golem_screen.dart';

typedef SummonScreenBuilder = Widget Function(BuildContext context);

class SummonEntry {
  final String id; // e.g. "clay_golem"
  final String title; // e.g. "Clay Golem"
  final bool enabled;
  final SummonScreenBuilder builder;

  const SummonEntry({
    required this.id,
    required this.title,
    required this.enabled,
    required this.builder,
  });
}

class SummonClass {
  final String id; // e.g. "necromancer"
  final String title; // e.g. "Necromancer"
  const SummonClass({required this.id, required this.title});
}

class SummonsRegistry {
  // ---- Classes (variant-aware) ----

  static List<SummonClass> classesForVariant({
    required String gameId,
    required String variantId,
  }) {
    // For now: D2R Vanilla supports these classes in Summons module.
    if (gameId == 'diablo2' && variantId == 'd2r_vanilla') {
      return const [
        SummonClass(id: 'necromancer', title: 'Necromancer'),
        SummonClass(id: 'druid', title: 'Druid'),
        SummonClass(id: 'sorceress', title: 'Sorceress'),
        SummonClass(id: 'paladin', title: 'Paladin'),
        SummonClass(id: 'assassin', title: 'Assassin'),
        SummonClass(id: 'amazon', title: 'Amazon'),
        SummonClass(id: 'barbarian', title: 'Barbarian'),
      ];
    }

    return const [];
  }

  // ---- Summons list (variant + class aware) ----

  static List<SummonEntry> summonsFor({
    required String gameId,
    required String variantId,
    required String classId,
  }) {
    if (gameId == 'diablo2' && variantId == 'd2r_vanilla') {
      switch (classId) {
        case 'necromancer':
          return const [
            SummonEntry(
              id: 'clay_golem',
              title: 'Clay Golem',
              enabled: true,
              builder: _buildClayGolem,
            ),
            SummonEntry(
              id: 'blood_golem',
              title: 'Blood Golem',
              enabled: false,
              builder: _buildPlaceholder,
            ),
            SummonEntry(
              id: 'iron_golem',
              title: 'Iron Golem',
              enabled: false,
              builder: _buildPlaceholder,
            ),
            SummonEntry(
              id: 'fire_golem',
              title: 'Fire Golem',
              enabled: false,
              builder: _buildPlaceholder,
            ),
            SummonEntry(
              id: 'raise_skeleton',
              title: 'Raise Skeleton',
              enabled: false,
              builder: _buildPlaceholder,
            ),
            SummonEntry(
              id: 'raise_skeletal_mage',
              title: 'Raise Skeletal Mage',
              enabled: false,
              builder: _buildPlaceholder,
            ),
            SummonEntry(
              id: 'revive',
              title: 'Revive',
              enabled: false,
              builder: _buildPlaceholder,
            ),
          ];

        case 'druid':
          return const [
            SummonEntry(id: 'raven', title: 'Raven', enabled: false, builder: _buildPlaceholder),
            SummonEntry(id: 'summon_spirit_wolf', title: 'Spirit Wolf', enabled: false, builder: _buildPlaceholder),
            SummonEntry(id: 'summon_dire_wolf', title: 'Dire Wolf', enabled: false, builder: _buildPlaceholder),
            SummonEntry(id: 'summon_grizzly', title: 'Grizzly', enabled: false, builder: _buildPlaceholder),
            SummonEntry(id: 'poison_creeper', title: 'Poison Creeper', enabled: false, builder: _buildPlaceholder),
          ];

        default:
          // Other classes: no summons implemented yet
          return const [];
      }
    }

    return const [];
  }

  // ---- Builders ----

  static Widget _buildClayGolem(BuildContext context) => const ClayGolemScreen();

  static Widget _buildPlaceholder(BuildContext context) {
    return const Scaffold(
      body: Center(child: Text('Coming soon')),
    );
  }
}
