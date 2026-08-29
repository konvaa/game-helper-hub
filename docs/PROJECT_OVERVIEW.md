# Game Helper — přehled projektu

## Co to je

Nástroj pro hráče (zatím Diablo 2 / Diablo 2: Resurrected), který počítá odvozené
staty herních mechanik — v první fázi **summony** (přivolaná stvoření: Raise
Skeleton, Skeletal Mage, golemové, ...) — na základě úrovně skillu, staticky
vyexportovaných herních dat a D2 výpočetních vzorců (ternární operátory,
lineární/segmentované škálování přes úrovně).

Cíl je data-driven: nový summon/skill se má dát přidat přes JSON dataset a
dat v `skills_raw`, bez zásahu do jádra kalkulátoru. Viz `docs/ROADMAP.md`
pro seznam plánovaných skillů napříč třídami.

## Plánovaná / současná architektura

```
CascView export (GUI, ruční krok)
        │  D2R .txt (TSV) soubory z game data/global/excel
        ▼
scripts/rebuild_dataset.py  ──▶  data/.../generated/*.json   (raw + částečně normalizovaná data)
        │
        ▼
scripts/summon_engine/  (loader.py, expr.py, runner.py, stats.py)
        │  evaluátor D2 výrazů (ternary, seg5/lin/dm, skill('X').lvl reference)
        ▼
scripts/summon_models/  (raise_skeleton.py, raise_skeletal_mage.py, clay_golem.py, ...)
        │  konkrétní model per summon, staví na enginu a sloupcích skills_raw
        ▼
scripts/summon_cli.py  ──▶  JSON výstup (staty summonu pro danou kombinaci úrovní)
```

Plánovaný, zatím nerealizovaný zbytek pipeline (viz `docs/AUDIT_2026-08-28.md`,
sekce 2.2–2.3): relační schéma → import do PostgreSQL → Java/Spring Boot API →
zobrazení (buď oprava `mobile_app/` Flutter klienta, nebo napojení na API).
Fáze 2 (vyhledávání/filtry), 3 (build planner) a 4 (účty) jsou zatím na 0 %.

## Stav (viz audit pro detaily)

| Vrstva | Stav |
|---|---|
| Data (export z CascView + `rebuild_dataset.py`) | funkční, `index.json` říká z jaké verze D2R |
| Python engine (`summon_engine/`) | funkční, 17/17 golden testů (RS 8/8, RS Mage 9/9) |
| Modely summonů (`summon_models/`) | 3 z ~20 plánovaných (Raise Skeleton, Skeletal Mage, Clay Golem) |
| Flutter app (`mobile_app/`) | UI kostra, zatím bez napojení na reálná data |
| Java/Spring Boot backend | neexistuje |
| PostgreSQL | neexistuje |

Aktuální stav a rizika k datu auditu jsou v `docs/AUDIT_2026-08-28.md` —
ten dokument se needituje průběžně, je to snapshot k danému datu.

## Struktura repozitáře (klíčové adresáře)

- `data/diablo2/resurrected/helpers/generated/` — vygenerovaná data z CascView exportu
  (viz `docs/DATA_FORMAT.md`). Negenerují se automaticky ze scraping — jediný
  zdroj je ruční CascView export, viz `index.json.excel_dir`.
  (Adresář `overrides/` byl 2026-08-28 odstraněn — obsahoval konfiguraci,
  kterou nečetl žádný kód. Viz `docs/DATA_FORMAT.md`.)
- `raw_sources/` — vstupní podklady/poznámky mimo generovaný dataset.
- `scripts/summon_engine/` — jádro: načítání datasetu, evaluátor výrazů,
  běhové statistiky (`stats.py`), spouštěč (`runner.py`).
- `scripts/summon_models/` — jeden modul na summon; každý model skládá
  finální staty z `engine` + `overrides`.
- `scripts/tests/` — golden-test sady (baseline JSON + pytest wrappery, které
  spouští `summon_cli.py` jako subprocess a porovnávají výstup s baseline).
- `scripts/tools/` — jednorázové diagnostické/pomocné skripty (např.
  `dump_skilldesc_rs.py`), nejsou součástí hlavního pipeline.
- `mobile_app/` — Flutter klient (UI kostra, viz audit sekce 1.4 pro známé
  nedostatky — schéma dat v Dart enginu zatím neodpovídá tomu, co generuje
  Python).
- `docs/` — tento přehled, `DATA_FORMAT.md`, `ROADMAP.md`, audity.

## Jak spustit testy

```bash
pip install -r requirements.txt
pytest scripts/tests/ -v
```

`conftest.py` v kořeni repozitáře přidá `scripts/` na `PYTHONPATH` automaticky
(pro přímé importy i pro subprocess volání `summon_cli.py` z testů), není
potřeba nic ručně exportovat.

## Jak přegenerovat dataset

1. V CascView ručně exportovat `data/global/excel` z aktuální instalace D2R
   (GUI nástroj, nemá CLI — tenhle krok nejde automatizovat).
2. Ověřit/aktualizovat `excel_dir` v
   `data/diablo2/resurrected/helpers/generated/index.json`.
3. Spustit `python scripts/rebuild_dataset.py` — přepíše JSON soubory v
   `generated/`.
4. Spustit `pytest scripts/tests/ -v` a ověřit, že baseline testy dál procházejí
   (pokud CascView export mění hodnoty použité v golden testech, baseline je
   potřeba případně přegenerovat přes `scripts/tests/generate_baseline_*.py`).
