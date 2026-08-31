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
    - [ ] Iron Golem — **premium funkce** (viz Produktová rozhodnutí)
    - [ ] Fire Golem — **odloženo na konec** (viz Produktová rozhodnutí)
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


## Produktová rozhodnutí

Rozhodnuto 2026-08-30.

### Iron Golem = premium funkce

Iron Golem odvozuje staty z **konkrétního itemu**, ze kterého je vytvořen
(base item + affixy + runeword). To z něj dělá jedinou mechaniku v plánu,
která potřebuje kompletní model itemu — a zároveň nejvyšší přidanou hodnotu
pro hráče, protože ručně se to spočítat prakticky nedá.

- **Premium:** výpočet z konkrétního zadaného itemu.
- **Free:** jen základní hodnoty golema bez itemu.

Důsledek pro architekturu: model itemu (base + affixy + runewords) je nutná
podmínka premium varianty a v odhadech na něj musí být místo. Free varianta
ho nepotřebuje, takže se dá dodat dřív.

### Fire Golem odložen na konec

Fire Golem jde na konec pořadí golemů. Ostatní summony mají vyšší prioritu.

