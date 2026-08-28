# Roadmap (high level)

## Diablo 2 / Diablo 2 Resurrected — Helpers

### Summons (priority)
- [x] Necromancer — MVP
  - [ ] Skeleton Warrior
  - [ ] Skeleton Mage
  - [ ] Skeleton Mastery
  - [ ] Golems:
    - [ ] Clay Golem
    - [ ] Blood Golem
    - [ ] Iron Golem (hardest: item-based stats)
    - [ ] Fire Golem
  - [ ] Revive (later)

- [ ] Druid
  - Wolves:
    - [ ] Spirit Wolf
    - [ ] Dire Wolf
  - [ ] Grizzly
  - [ ] Ravens
  - Vines:
    - [ ] Poison Creeper
    - [ ] Carrion Vine
    - [ ] Solar Creeper
  - Spirits (later):
    - [ ] Oak Sage
    - [ ] Heart of Wolverine
    - [ ] Spirit of Barbs

- [ ] Assassin
  - [ ] Shadow Warrior
  - [ ] Shadow Master

- [ ] Amazon
  - [ ] Valkyrie

Notes:
- Data-driven: each helper should be addable via JSON dataset without rewriting calculator core.
- Iron Golem will require an "item snapshot" model (base item stats + affixes + runeword) and then conversion rules to golem stats.
