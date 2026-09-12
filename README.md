# Game Helper

Nástroj pro hráče Diablo II: Resurrected, který počítá odvozené staty
herních mechanik (v první fázi **summony** — přivolaná stvoření: Raise
Skeleton, Skeletal Mage, golemové, ...) na základě úrovně skillu a
staticky vyexportovaných herních dat. Podrobný přehled projektu, včetně
plánované architektury a stavu jednotlivých vrstev: [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md)

Stav: REST API nad datasetem summonů běží (Spring Boot, PostgreSQL,
Flyway, Docker). Flutter klient zatím nezačal. Python implementace ve
`scripts/` slouží jako reference a zdroj baseline dat pro testy.

## Spring Boot API (aktuálně aktivní práce)

**[`backend/`](backend/README.md)** — REST API nad daty D2R, portované
z Python reference implementace, viz níže. Spustíš jedním příkazem
(`docker compose up --build`), viz [`backend/README.md`](backend/README.md).

## Ostatní části repozitáře

- **`scripts/`** — Python referenční implementace: `rebuild_dataset.py`
  (staví JSON dataset z exportovaných herních `.txt` souborů),
  `summon_engine/` (evaluátor D2 herních výrazů), `summon_models/`
  (modely jednotlivých summonů), `summon_cli.py` (CLI pro ruční výpočet).
  Baseline data pro Java testy (`scripts/tests/`) pocházejí odtud.
- **`data/`** — místo pro zpracovaný dataset. Herní data nejsou součástí
  repozitáře. Vygeneruješ si je ze své vlastní instalace hry pomocí
  `scripts/rebuild_dataset.py`; struktura je vidět na ukázkových souborech
  `*.example.json`.
- **`docs/`** — plán, průběžné poznámky a zdůvodnění rozhodnutí k celému
  projektu; rozcestník je
  [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md).
- **`mobile_app/`** — Flutter klient, zatím nerealizovaný (viz
  `docs/PROJECT_OVERVIEW.md`, plánovaná architektura).
