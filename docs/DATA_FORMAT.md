# Datový formát

Popisuje soubory v `data/diablo2/resurrected/helpers/generated/` a
`data/diablo2/resurrected/helpers/overrides/`, tak jak je produkuje
`scripts/rebuild_dataset.py` a jak je čte `scripts/summon_engine/`.

## Původ dat

Žádný scraping. Zdrojem jsou D2R `.txt` (ve skutečnosti TSV) soubory z
`data/global/excel`, ručně vyexportované nástrojem **CascView** (GUI, bez
CLI) z lokální instalace hry. Cesta k exportu je v
`generated/index.json.excel_dir` — tam je vidět, ze které verze D2R data
pochází. `rebuild_dataset.py` je TSV → JSON dumper: čte tyto `.txt` soubory a
zapisuje `generated/*.json`.

## Obecný tvar

Každý `generated/*.json` soubor (kromě `monlvl_ratios.json` a
`skills_refs.json`) má tvar:

```json
{
  "meta": { "schema": "<jméno>.v1", ... },
  "<top_key>": { "<klíč_záznamu>": { ... }, ... }
}
```

`<top_key>` je dict, ne list — klíčem je buď herní interní jméno (skill,
missile, state, ...), nebo normalizovaný klíč. Klíče se čtou přes
`normalize_key()` v `loader.py` (lowercase, mezery/pomlčky → `_`), takže
`"Raise Skeleton"` i `"raise_skeleton"` míří na stejný záznam.

## `skills_raw.json`

429 skillů, **322 sloupců na skill jako doslovné stringy** — přímý dump
`skills.txt`, bez normalizace typů. To je zdroj dat pro `expr.py` evaluátor
(sloupce jako `Param1..12`, `EMin`, `EMinLev1..5`, `srvstfunc`, `*calc*`
apod.) a pro `skills_refs.json` (graf závislostí mezi skilly).

Projekce téhle tabulky na užitečnou podmnožinu (~25 sloupců skutečně
používaných evaluátorem/testy, se sémantikou z D2R DataGuide) je plánovaná
práce pro P1 relační schéma — zatím neprovedená. Seznam sloupců k zachování
vznikne až jako výstup téhle analýzy; žádný takový seznam zatím neexistuje.

## `skilldesc_raw.json`

263 záznamů, 120 sloupců — dump `skilldesc.txt` (popisné/display řetězce a
`*calc*` sloupce pro UI tooltipy). Párováno se `skills_raw` přes display name
skillu (`skills_raw[x].skill` → hledá se case-insensitive v `skilldesc_raw`
klíčích), ne přes interní klíč — viz `scripts/tools/dump_skilldesc_rs.py` pro
příklad tohoto lookupu.

## `monsters_base.json` — **jediná skutečně normalizovaná entita**

752 monster záznamů. Na rozdíl od `*_raw.json` souborů má vlastní typované
schéma, ne doslovný TSV dump:

```json
{
  "id": "skeleton1",
  "name_str": "Skeleton",
  "base": {
    "normal":    { "level": 2, "min_hp": 86, "max_hp": 129, "ac": 84,
                    "a1_min_d": 34, "a1_max_d": 101, "a1_th": 101,
                    "a2_min_d": 34, "a2_max_d": 101, "a2_th": 101,
                    "s1_min_d": null, "s1_max_d": null, "s1_th": null },
    "nightmare": { "...": "stejná pole, jiné hodnoty" },
    "hell":      { "...": "stejná pole, jiné hodnoty" }
  }
}
```

Per-difficulty bloky (`normal` / `nightmare` / `hell`), typované hodnoty
(int/float/null, ne string). `a1_*`/`a2_*` = Attack1/Attack2 (min/max damage,
to-hit), `s1_*` = Skill1 damage (může být `null`). `loader.py:
extract_monster_base_stats()` z tohohle skládá `(hp, dmin, dmax, ac, th)` pro
danou obtížnost.

## `missiles_raw.json`

742 záznamů. Normalizovanější pohled než `skills_raw` — každý záznam má
`id`, `row` (surová TSV data missile.txt), a odvozená pole `etype`, `emin`,
`emax`, `elen`, `minelev`, `maxelev` (elementární škálování přes úrovně
5-segmentů — stejný `seg5()` princip jako u skillů). `loader.py:
extract_missile_elem_scaling()` čte tohle.

## `pettype_raw.json`, `states_raw.json`, `itemstatcost_raw.json`

Doslovné TSV dumpy `pettype.txt` (22), `states.txt` (232, 75 sloupců —
stavové efekty/aury), `itemstatcost.txt` (368, 52 sloupců — definice item
statů). V současném enginu se používají jen okrajově; jsou v datasetu pro
budoucí modely (item-based golemové, buffy).

## `monlvl_ratios.json`

Bez `meta`/top_key obálky navíc — top-level klíče rovnou `normal` /
`nightmare` / `hell`, každý dict s poměrovými konstantami (`ac` a další) pro
škálování monster statů podle levelu. Používá se tam, kde monster nemá
přímý řádek pro danou kombinaci (odvození přes poměr).

## `skills_refs.json`

Graf závislostí mezi skilly (`edges`): které skilly na sebe odkazují přes
`skill('X').lvl` v D2 výrazech (příklad: Raise Skeleton odkazuje na Skeleton
Mastery). 153 hran, `unresolved` (aktuálně 0) — odkazy, které se nepodařilo
spárovat se skutečným skillem; užitečné jako kontrola konzistence po
refreshi datasetu.

## `overrides/skills_effects.json`

Ruční mapování, protože `skills_raw` sloupce obsahují jen sémantiku hry
(D2 výrazy jako `max(0, (lvl >= 29) ? (4*lvl - 74) : floor(((lvl-6)*(lvl-2))/15))`),
ne přímo "tenhle skill ovlivňuje tenhle výstup takhle". Pro každý summon
definuje: zdroj monstra (`monster_source`), zdrojový attack slot
(`base_attack_slot`), výrazy pro flat/percent bonusy, a
`extra_flat_from_skills` (např. Skeleton Mastery přidává flat damage k Raise
Skeleton). Aktuálně pokrývá pilotně Raise Skeleton + Skeletal Mage — rozšíření
na další summony je ruční práce per skill.

## `ui_catalog.json`

Statické seskupení skillů podle třídy/kategorie pro UI (které summony patří
které postavě). Není index ani fulltext — jen ruční hierarchie
`classes.<třída>.groups.<skupina> = [skill_key, ...]`. Fáze 2 (vyhledávání a
filtry) tohle nahradí něčím dynamičtějším.

## Konvence napříč soubory

- Čísla v `*_raw.json` souborech (kromě `monsters_base.json`) jsou **stringy**,
  i když reprezentují čísla — `loader.py` má `_to_int`/`_to_float` helpery
  pro bezpečný převod (vrací `None` na prázdný/nekonvertovatelný string).
  Nekonvertovat ručně přes `int()`/`float()` v novém kódu — použít tyhle
  helpery, jinak spadne na prázdné buňce.
- Klíče záznamů se porovnávají přes `normalize_key()`, ne přímým stringovým
  srovnáním.
- **Known bug (neopraveno):** `scripts/tools/dump_skilldesc_rs.py` tuhle
  konvenci porušuje — hledá natvrdo `"raise_skeleton"`, ale klíč v datech je
  `"Raise Skeleton"`, takže lookup selže a skript skončí na
  `[FAIL] raise_skeleton not found in skills_raw.json`. Není to popis
  zamýšleného chování, je to chyba v tom skriptu. Fix = pustit obě strany
  porovnání přes `normalize_key()`.
- `index.json` je jediné místo, které říká, ze které verze D2R dataset
  pochází (`excel_dir` obsahuje verzi ve své cestě, např. `D2R33`) — při
  refreshi datasetu je potřeba ho zkontrolovat/aktualizovat jako první krok.
