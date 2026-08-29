# Projekce `skills_raw` (322 → ~25 sloupců)

Podklad pro relační schéma v P1 (Java/Spring Boot slice).
**Toto je analýza, ne migrace** — žádný kód ani data se tímto dokumentem nemění.

Datová základna: dataset z D2R 3.3 (`generated/index.json.excel_dir` →
`F:\Apps\CascView\Work\D2R33\...`), 429 skillů × 322 sloupců.

---

## 0. Metodika a co je čím ověřené

Označení zdroje u každého tvrzení v tomto dokumentu:

| značka | význam |
|---|---|
| **[kód]** | ověřeno čtením `scripts/summon_engine/` a `scripts/summon_models/` |
| **[data]** | ověřeno spuštěním analýzy nad `skills_raw.json` (429 řádků) |
| **[guide]** | převzato z D2R Data Guide |

**Omezení, které je potřeba znát:** `locbones.github.io/D2R_DataGuide/` je jediná
obří `index.html`; automatické načtení ji pokaždé usekne dřív, než dojde k sekci
Skills.txt. Z původního zdroje se proto podařilo spolehlivě získat jen **legendu
typů**. Popisy jednotlivých sloupců pocházejí ze stránkované varianty téhož
průvodce (`d2r-tools.com/data/skills`, `d2r-tools.com/data/skilldesc`).

Ta varianta ale vrátila **typové značky nedůvěryhodně** — označila `[N]` i pro
zjevně textové sloupce (`skill`, `EType`, `summon`), což odporuje legendě. Typy
v tomto dokumentu jsou proto **odvozené z obsahu dat** (klasifikace všech 429
hodnot každého sloupce) a označené **[data]**. Kde se typ shoduje i s guide,
je uvedeno **[guide]**.

### Legenda typů (ověřeno z DataGuide)

| značka | význam (citace) |
|---|---|
| `[*]` | "Comment column. This column is not read by the game and is used for documentation or indexing purposes only" |
| `[B]` | "Boolean column. Only understands the values 0 and 1." |
| `[N]` | "Number column. Only understands static numbers; usually used as skill parameter or item stat value." |
| `[O]` | "Open column. Understands text, numbers and limited set of special characters; usually used as references to other file entries. **Cannot use calculations.**" |
| `[C]` | "Calculation column. Can use math arithmetic for dynamic values. Can also use static numbers" |
| `[X]` | "Broken column. This column is broken or highly restrictive." |

---

## 1. Sloupce, které kód skutečně čte

### 1.1 Čtené přímo jménem

| sloupec | kde | k čemu |
|---|---|---|
| `skill` | `loader.py` (klíč záznamu) | identita, normalizuje se přes `normalize_key()` |
| `Param1`–`Param12` | `loader.py: make_vars()` | naplní `par1..par12` do `EvalContext.vars`; **vstup pro každý výraz** |
| `summon` | `loader.py: pick_summon_monster_id()` | ID monstra do `monsters_base` |
| `EMin`, `EMinLev1`–`EMinLev5` | `raise_skeleton.py` přes `get_skill_field_float()` | `seg5()` → interní flat damage bonus |
| `petmax` | `raise_skeletal_mage.py` | počet přivolaných jednotek |
| `sumsk1calc` | `raise_skeletal_mage.py` | RSL = úroveň missile skillu mága |
| `calc1` | `clay_golem.py` | HP% modifikátor (včetně Golem Mastery a Blood synergie) |
| `passivestat1`–`14` + `passivecalc1`–`14` | `runner.py: extract_passives()` | pasivní staty, vyhodnocené evaluátorem |
| `passivecalc2`, `passivecalc3` | `clay_golem.py` | to-hit flat, damage % |

### 1.2 Čtené nepřímo přes evaluátor

`expr.py` neřeší sloupce jménem, ale vyhodnocuje jejich **obsah**. Tokeny,
které v těch výrazech potkává, určují, jaká data musí být v DB dostupná:

| token | co dělá | důsledek pro schéma |
|---|---|---|
| `parN` | přímý odkaz na `ParamN` | `Param*` musí být uložené |
| `lnAB` | `parA + (lvl-1) * parB` — **lineární, neomezené** | dtto |
| `dmAB` | asymptotická křivka mezi `parA` a `parB` | dtto |
| `skill('X'.lvl)` | **efektivní** úroveň jiného skillu | nutná identita skillů + cross-reference |
| `skill('X'.blvl)` | **base** úroveň jiného skillu (jen investované body) | viz sekce 6 |
| `skill('X'.parN)` | parametr jiného skillu | `Param*` napříč skilly |
| `seg5(...)` | segmentové škálování | `EMin`/`EMax` + `*Lev1..5` |

### 1.3 Zjištění: `overrides/skills_effects.json` je mrtvá konfigurace

**[kód]** Ten soubor **nečte žádný kód**. (Shody na „overrides" ve `summon_cli.py`
a `runner.py` jsou nesouvisející — jde o `--set` přepisy úrovní skillů.)

Obsahuje přitom napevno zadaný polynom pro Raise Skeleton:

```
max(0, (lvl >= 29) ? (4*lvl - 74) : floor(((lvl-6)*(lvl-2))/15))
```

Porovnání proti `seg5()` z reálných dat (`EMin=0`, `Lev1..5 = 0,1,2,3,4`) **[data]**:

| lvl | 16 | 20 | 21 | 22 | 23 | 29 | 40 | 50 |
|---|---|---|---|---|---|---|---|---|
| `seg5` (kód) | 8 | 16 | 18 | 20 | 23 | 42 | 86 | 126 |
| polynom (overrides) | 9 | 16 | 19 | 21 | 23 | 42 | 86 | 126 |
| rozdíl | **+1** | 0 | **+1** | **+1** | 0 | 0 | 0 | 0 |

Upřesnění proti auditu (bod 2.1.3): polynom se nerozchází „od lvl 22", ale
**přesně na úrovních 16, 21 a 22**, vždy o +1. Od 23 výš sedí, včetně celé
větve pro 29+. Je to tedy proložení křivky, které selhává na třech konkrétních
bodech — ne systematický rozjezd.

**Vyřešeno 2026-08-28:** soubor byl smazán (celý adresář `overrides/` je
pryč), aby ho nikdo nepoužil jako zdroj pravdy. Obsah zůstává v historii:
`git show 0c1b6a8:data/diablo2/resurrected/helpers/overrides/skills_effects.json`.
Pokud se deklarativní mapování někdy vrátí, nesmí duplikovat vzorce — jen
odkazovat na sloupce `skills_raw`, které je nesou.

---

## 2. Škálování nad lvl 20 — jádro věci

Aplikace existuje kvůli efektivním úrovním 35–40+, kde běžné tabulky do 20
končí. Tahle sekce má proto v projekci nejvyšší prioritu.

### 2.1 Jak `seg5` funguje

**[kód]** `expr.py:280`. Pět segmentů, každý s vlastním přírůstkem na úroveň.
Hranice **[guide]** („5 brackets: levels 2–8, 9–16, 17–22, 23–28, 29+") se
přesně shodují s implementací:

| segment | rozsah úrovní | přírůstek | max. počet kroků |
|---|---|---|---|
| — | lvl 1 | `EMin` (báze) | — |
| 1 | 2–8 | `EMinLev1` | 7 |
| 2 | 9–16 | `EMinLev2` | 8 |
| 3 | 17–22 | `EMinLev3` | 6 |
| 4 | 23–28 | `EMinLev4` | 6 |
| 5 | **29+** | `EMinLev5` | **neomezeně** |

### 2.2 Co platí pro efektivní úroveň 29+

**První čtyři segmenty jsou vyčerpané a zamrznou** na konstantě
`EMin + 7·Lev1 + 8·Lev2 + 6·Lev3 + 6·Lev4`. Od úrovně 29 výš přispívá
**už jen pátý segment**, a to lineárně a bez horní hranice:

```
hodnota(lvl) = EMin + 7·Lev1 + 8·Lev2 + 6·Lev3 + 6·Lev4 + (lvl − 28)·Lev5
```

Prakticky to znamená tři věci:

1. **Nad lvl 28 je růst čistě lineární** se sklonem `EMinLev5` na úroveň.
   Žádné další zlomy už nepřijdou — pátý segment nemá strop.
2. **`EMinLev5` / `EMaxLev5` jsou tím pádem nejdůležitější sloupce celé
   projekce** pro cílový scénář. U Raise Skeleton je `EMinLev5 = 4`, takže
   každá další +1 ze skills přidá +4 flat damage donekonečna.
3. Rozdíl proti tabulkám do lvl 20 je velký: Raise Skeleton má na lvl 20
   flat bonus **16**, na lvl 40 už **86** — víc než pětinásobek. **[data]**

**[data]** Nenulový `EMinLev5` má **105 z 429 skillů** — u nich všech je
chování nad lvl 28 dané tímhle jediným sloupcem.

### 2.3 Ostatní mechaniky škálování a jejich chování nad 20

| mechanika | vzorec | chování nad lvl 20 |
|---|---|---|
| `seg5` | viz výše | lineární, **neomezené** |
| `lnAB` | `parA + (lvl−1)·parB` | lineární, **neomezené** — žádné segmenty |
| `dmAB` | `mn + (mx−mn)·lvl/(lvl+5.5)` | **asymptotické** k `parB`, nikdy ho nepřekročí |
| ternární prahy | `(lvl >= N) ? a : b` | skoková změna na pevné úrovni |

### 2.4 Riziko, které je potřeba pojmenovat: `dm` křivka

**[kód]** `expr.py: _dm_curve()` má v docstringu:

> "Curve: mn + (mx-mn) * lvl / (lvl + K) with K tuned to 5.5 so that floor()
> matches in-game tooltip **at lvl1**."

Ta konstanta `K = 5.5` je **proložení kalibrované na úrovni 1**. Pro cílový
scénář (lvl 35–40+) není nijak ověřená — a `dm` se používá právě tam, kde na
tom záleží: Clay Golem `aurastatcalc1 = dm34` (slow %) a
`passivecalc1 = skill('Golem Mastery'.dm34)` (velocity %).

Baseline testy tohle nepokrývají — 17 ověřených případů je jen pro Raise
Skeleton a Skeletal Mage, které `dm` nepoužívají. **Clay Golem nemá ani jeden
golden test.** Evidováno v [`KNOWN_ACCURACY_GAPS.md`](KNOWN_ACCURACY_GAPS.md),
bod 1 (problém A). Než se na golemy postaví DB vrstva, měla by se ověřit
proti hře na vysokých úrovních; jinak se do schématu zabetonuje neověřená
aproximace. Není to blocker pro projekci sloupců, ale je to blocker pro
důvěru ve výsledky u golemů.

### 2.5 `maxlvl` = 20 není strop výpočtu

**[data]** 260 skillů má `maxlvl = 20`, zbylých 169 ho má prázdný.
**[guide]** `maxlvl` = "Maximum **base** level (excluding +N to skills from items)".

Je to tedy strop **investovaných bodů**, ne efektivní úrovně. Schéma ho musí
uložit (kvůli validaci vstupu „kolik bodů smí hráč dát"), ale výpočet se jím
**nesmí** omezovat. To je přesně ten rozdíl, kvůli kterému appka vzniká.

---

## 3. Navrhovaná projekce

Cílový tvar: **26 sloupců** v hlavní tabulce `skill` + tři podřízené tabulky
pro opakující se skupiny. Typy jsou PostgreSQL.

Poznámka k `NULL`: v `skills_raw` je „prázdno" reprezentované prázdným
stringem, ne `NULL`. Import musí `""` převádět na `NULL`, jinak se rozbije
každý číselný převod (`loader.py` na to má `_to_int`/`_to_float`, které
vrací `None`).

### 3.1 Hlavní tabulka `skill`

#### A. Identita a klíče (6)

| # | sloupec | typ dle dat | SQL | proč |
|---|---|---|---|---|
| 1 | `skill` | `[O]` **[data]** | `TEXT NOT NULL UNIQUE` | jediný lidsky čitelný identifikátor; kód na něj míří přes `normalize_key()` |
| 2 | `*Id` | `[*]` **[guide]** | `INTEGER` | viz poznámka níže |
| 3 | `charclass` | `[O]` | `TEXT NULL` | třída (`nec`, `ama`…); vyplněno u 240/429. Nutné pro filtry ve Fázi 2 |
| 4 | `skilldesc` | `[O]` | `TEXT NULL` → FK `skilldesc(skilldesc)` | odkaz do `skilldesc_raw`; vyplněno u 283/429 |
| 5 | `reqlevel` | `[N]` | `SMALLINT` | min. úroveň postavy; validace buildu ve Fázi 3 |
| 6 | `maxlvl` | `[N]` | `SMALLINT NULL` | strop **investovaných bodů** (20). Viz 2.5 — nesmí omezovat výpočet |

> **`*Id` je formálně `[*]`, tedy komentářový sloupec, který hra nečte** —
> engine určuje ID pořadím řádků. Přesto ho do schématu dávám: je to jediný
> stabilní číselný identifikátor v exportu a jako surrogate klíč se hodí. Musí
> ale být jasné, že **není autoritativní** — při změně pořadí řádků v novém
> exportu se může posunout. Autoritativní klíč je `skill`.

#### B. Segmentové škálování — nejvyšší priorita (13)

| # | sloupec | SQL | proč |
|---|---|---|---|
| 7 | `EType` | `TEXT NULL` | typ živlu (`fire`/`cold`/`ltng`/`pois`/`mag`) |
| 8 | `EMin` | `INTEGER NULL` | báze min. elementálního dmg na lvl 1 |
| 9 | `EMax` | `INTEGER NULL` | dtto max |
| 10–14 | `EMinLev1`…`EMinLev5` | `INTEGER NULL` ×5 | přírůstky min. dmg po segmentech |
| 15–19 | `EMaxLev1`…`EMaxLev5` | `INTEGER NULL` ×5 | přírůstky max. dmg po segmentech |

**Tohle je jádro celé projekce.** `EMinLev5`/`EMaxLev5` určují chování nad
lvl 28 (sekce 2.2). Raise Skeleton používá `EMin` + `EMinLev1..5` přímo přes
`get_skill_field_float()`. **[kód]**

#### C. Summon a poutu k monstru (4)

| # | sloupec | SQL | proč |
|---|---|---|---|
| 20 | `summon` | `TEXT NULL` → FK `monster(id)` | ID přivolaného monstra; `loader.py: pick_summon_monster_id()` **[kód]** |
| 21 | `pettype` | `TEXT NULL` → FK `pettype(id)` | sdílené vlastnosti petů |
| 22 | `petmax` | `TEXT NULL` | **výraz**, ne číslo — `(lvl < 4) ?lvl:(2+lvl/3)`. Musí být `TEXT` |
| 23 | `HitShift` | `SMALLINT NULL` | bitový posun přesnosti dmg (8 = 256/256). Vyplněno u 414/429 |

#### D. Výrazové sloupce (3)

| # | sloupec | SQL | proč |
|---|---|---|---|
| 24 | `calc1` | `TEXT NULL` | Clay Golem HP% **[kód]**; u RS/RSM nese HP% vzorec |
| 25 | `aurastat1` + `aurastatcalc1` | `TEXT NULL` ×2 | u Raise Skeleton nese `damagepercent` a `((lvl<4)?0:((lvl-3)*par3))` |
| 26 | `SrcDam` | `SMALLINT NULL` | % dmg zbraně přenesené na skill (ze 128). Hraniční — viz níže |

### 3.2 Podřízené tabulky

Tři skupiny se opakují natolik, že do hlavní tabulky nepatří:

```sql
-- Param1..Param20  (nahrazuje 20 sloupců)
skill_param(skill_id, idx SMALLINT, value INTEGER, description TEXT)
--   description = obsah odpovídajícího '*ParamN Description' [*] sloupce.
--   Není to herní data, ale pro UI a ladění je to zlato.

-- calc1..calc10, aurastatcalc1..6, passivecalc1..14, sumsk1..5calc, petmax
skill_calc(calc_id PK, skill_id, slot TEXT, expr TEXT)
--   slot = 'calc1' | 'aurastatcalc1' | 'passivecalc3' | ...

-- Odkazy na jine skilly, vytazene z vyrazu. Jeden vyraz jich muze mit vic
-- a KAZDY muze cist jinou uroven - viz sekce 5.2. Proto samostatna tabulka,
-- ne sloupec na skill_calc a uz vubec ne priznak na skill.
skill_calc_ref(calc_id FK, target_skill TEXT, token TEXT, level_mode TEXT)
--   token      = 'blvl' | 'lvl' | 'ln12' | 'dm34' | 'par8' | ...
--   level_mode = 'base'      pro .blvl
--              | 'effective' pro .lvl, .lnAB, .dmAB
--              | 'none'      pro .parN (parametr bez urovne)

-- passivestat1..14, aurastat1..6, sumskill1..5
skill_stat_ref(skill_id, kind TEXT, idx SMALLINT, stat TEXT)
--   kind = 'passive' | 'aura' | 'sumskill'
```

Tenhle rozpad je důvod, proč se hlavní tabulka vejde do 26 sloupců: bez něj
by jen `Param*` + `*calc*` + `*stat*` zabraly přes 80 sloupců.

### 3.3 Hraniční sloupce

Zařazené, ale s výhradou — používají se okrajově, nebo až pro budoucí summony:

| sloupec | proč je hraniční |
|---|---|
| `SrcDam` | žádný současný model ho nečte **[kód]**; vyplněno jen u 74/429. Bude potřeba u Revive a Iron Golema (dmg odvozený od zdroje) |
| `HitShift` | žádný model ho nečte, ale je vyplněný u 414/429 a bez něj nejde správně škálovat elementální dmg (256/256). Levné uložit, drahé dodělávat |
| `EMax`, `EMaxLev1..5` | Raise Skeleton čte jen `EMin` větev **[kód]**. Pro Skeletal Mage a elementální summony ale bude `EMax` nutný — a vynechat půlku segmentové sady by bylo svévolné |
| `pettype` | zatím nikde nečteno; nutné, až přijde limit počtu petů napříč typy (golemové vs. skeletoni) |
| `EType` | čten jen nepřímo přes `missiles_raw` **[kód]**; u samotného skillu ho zatím nic nepoužívá |
| `*Id` | formálně `[*]`, viz poznámka v 3.1.A |

### 3.4 Sloupce potřebné až pro budoucí summony

Nejsou v projekci, ale **při rozšíření na golemy a Revive je bude potřeba
doplnit** — uvádím je, aby se na ně nezapomnělo:

- `sumskill1..5` + `sumsk1..5calc` — Skeletal Mage už `sumsk1calc` čte **[kód]**;
  patří do `skill_calc` / `skill_stat_ref`
- `aurastat2..6` + `aurastatcalc2..6` — víc než jeden aura stat
- `passivestat2..14` + `passivecalc2..14` — Clay Golem už čte `passivecalc2/3` **[kód]**
- `ELen`, `ELevLen1..3` — trvání elementálních efektů; Skeletal Mage je řeší
  přes `missiles_raw`, ale u jiných skillů sedí přímo tady
- `calc2..calc10` — Clay Golem používá `calc1`, další modely sáhnou dál

---

## 4. Co nezahrnuji a proč

Z 322 sloupců zbývá po projekci ~270 nepoužitých. Rozpadají se do těchto skupin.

### 4.1 Komentářové sloupce `[*]` — 35 sloupců **[data]**

Hra je nečte **[guide]**. Zjistitelné z hlavičky — začínají hvězdičkou:

```
*Id, *eol,
*calc1 desc … *calc10desc            (10)
*cltcalc1 desc … *cltcalc3 desc      (3)
*Param1 Description … *Param20Description  (20)
```

**Nezahrnuji je jako sloupce**, se dvěma výjimkami:

- `*Id` — zařazen jako surrogate klíč, viz 3.1.A
- `*ParamN Description` — **obsah** se přenáší do `skill_param.description`.
  Není to herní data, ale je to jediná dokumentace k tomu, co který `ParamN`
  u daného skillu znamená (u Raise Skeleton např. „flat damage bonus per
  level"). Pro UI a pro každého, kdo se v tom bude po půl roce hrabat, je to
  cennější než většina „skutečných" sloupců.
- `*eol` je čistý terminátor řádku — zahazuje se bez náhrady.

### 4.2 Sloupce `[X]` (rozbité)

**[guide]** Stránkovaná varianta průvodce **žádný sloupec ve Skills.txt jako
`[X]` neoznačuje**. Původní DataGuide se mi kvůli usekávání ověřit nepodařilo
(viz sekce 0), takže tohle tvrzení je slabší než ostatní v dokumentu.
Nezakládám na něm žádné rozhodnutí — vyloučené sloupce níž vylučuji z jiných
důvodů než „rozbité".

### 4.3 Klientská prezentace (~60 sloupců)

`cltdofunc`, `cltstfunc`, `cltprgfunc1..3`, `cltcalc1..3`, `anim`, `seqtrans`,
`monanim`, `seqnum`, `seqinput`, `itemcasteffect`, `itemcasteoverlay`,
`castoverlay`, `tgtoverlay`, `prgoverlay`, `cltmissile*`, `cltmissilea..d`,
`cltoverlaya/b`, `stsuccessonly`, `stsound*`, `castsound`, …

Animace, zvuky, overlaye a klientské efekty. Aplikace nekreslí hru, počítá
čísla. **Nulová hodnota pro výpočet.**

### 4.4 Serverová exekuce a cílení (~40 sloupců)

`srvstfunc`, `srvdofunc`, `srvprgfunc1..3`, `srvmissile*`, `prgstack`,
`prgcalc1..3`, `attackrank`, `aitype`, `aibonus`, `targetable`, `searchenemyxy`,
`searchenemynear`, `searchopenxy`, `selectproc` a další sloupce cílení.

Řídí, *jak* skill v enginu probíhá — ne *kolik* dělá. Výjimka: `srvdofunc`
a `srvstfunc` jsou vyplněné u 329 resp. 133 skillů **[data]** a rozlišují
*typ* chování skillu. **Nezahrnuji je** do projekce, ale pokud se ukáže, že je
potřeba skilly kategorizovat podle mechaniky, je to první kandidát na doplnění.

### 4.5 Náklady, cooldowny, požadavky (~25 sloupců)

`mana`, `minmana`, `manashift`, `startmana`, `lvlmana`, `delay`, `localdelay`,
`globaldelay`, `reqskill1..3`, `skpoints`, `weaponsel`, `itypea1..3`,
`etypea1..3`, `restrict`, `state1..3`, `interrupt`, `InTown`, `aura`, `periodic`…

Pro **build planner ve Fázi 3** budou `mana`, `reqskill1..3` a `skpoints`
potřeba (validace stromu, mana kalkulace). Pro P1, kde jde o staty summonů,
ne. Vědomé odložení, ne opomenutí.

### 4.6 Zbytek segmentových a symetrických sad (~50 sloupců)

`EDmgSymPerCalc`, `DmgSymPerCalc`, `ELenSymPerCalc`, `ToHitCalc`,
`auralencalc`, `aurarangecalc`, `aurafilter`, `MinDam`, `MaxDam`,
`MinLevDam1..5`, `MaxLevDam1..5`, `DmgSymPerCalc`, `ELevLen1..3`, …

Tady je to nejméně jednoznačné. `MinLevDam1..5`/`MaxLevDam1..5` jsou
**fyzická** obdoba `EMinLev*`/`EMaxLev*` se stejnou segmentovou logikou —
současné summony je nepoužívají (skeletoni berou fyzický dmg z `monsters_base`,
ne ze skillu), ale skilly s přímým fyzickým poškozením ano.

`EDmgSymPerCalc` **nezahrnuji do projekce, ale je to nejdůležitější vyloučený
sloupec** — nese synergie a je zdaleka největším uživatelem `blvl` v celém
souboru (159 výskytů **[data]**). Jakmile se model rozšíří ze summonů na
obecné skilly, tenhle sloupec je první na řadě. Viz sekce 5.

### 4.7 Shrnutí vyloučení

| skupina | ~počet | důvod |
|---|---|---|
| komentářové `[*]` | 35 | hra je nečte; `*ParamN Description` se přenáší jako text |
| klientská prezentace | ~60 | animace/zvuky, nulová hodnota pro výpočet |
| serverová exekuce a cílení | ~40 | „jak", ne „kolik" |
| náklady a požadavky | ~25 | až Fáze 3 (build planner) |
| ostatní segmentové/symetrické sady | ~50 | až při rozšíření mimo summony |
| zbytek (flagy, restrikce, item filtry) | ~60 | mimo rozsah |

---

## 5. Otevřené otázky k modelu postavy

Zadání znělo tohle zmapovat, ne vyřešit. Níž je to, co se z dat a kódu dá
doložit, a rozhodnutí, která z toho plynou.

### 5.1 D2 skutečně rozlišuje base a efektivní úroveň — doloženo daty

Není to teoretická otázka. `skills.txt` má pro odkaz na úroveň **jiného**
skillu čtyři různé zápisy a používá je záměrně různě **[data]**:

| zápis | režim | výskytů |
|---|---|---|
| `skill('X'.blvl)` | **base** — jen investované body (0–20) | **352** |
| `skill('X'.lnAB)` | efektivní (přes `ln` token cílového skillu) | 28 |
| `skill('X'.lvl)` | efektivní — včetně `+skills` z itemů | 20 |
| `skill('X'.dmAB)` | efektivní (přes `dm` token cílového skillu) | 13 |
| `skill('X'.parN)` | úroveň nenese vůbec (jen parametr) | 26 |

Celkem **413 odkazů nesoucích úroveň** na **138 různých cílových skillů**.
Mezi odkazy na cizí skilly tedy base režim výrazně převažuje (352 z 413).

> **Pozor na záměnu.** Vedle toho je v datech ještě **220 výskytů holého
> tokenu `lvl`** bez `skill()` — to je ale úroveň *vlastního* skillu, ne
> odkaz na cizí. Ta je vždy efektivní. Dřívější verze tohoto dokumentu ta
> dvě čísla zaměňovala a uváděla 220 jako počet `skill('X'.lvl)` odkazů.

Rozložení base odkazů podle sloupců:

| sloupec | `blvl` | co to je |
|---|---|---|
| `EDmgSymPerCalc` | 159× | synergie (elementální) |
| `calc1` | 43× | různé |
| `DmgSymPerCalc` | 29× | synergie (fyzická) |
| `sumsk1..5calc` | 21× | úrovně skillů dané petovi |
| `auralencalc` | 18× | délka aury |
| `petmax` | 6× | počet petů |

Rozložení **není** vzorec, ze kterého by šlo režim odvodit — viz 5.2.

### 5.2 Žádné pravidlo neexistuje — režim je vlastnost odkazu

> **Oprava.** Dřívější verze této sekce tvrdila pravidlo „synergie z hard
> pointů, mastery z efektivní úrovně". Analýza všech 429 skillů to vyvrací.
> Je to převažující případ, ne zákonitost.

Napříč `skills_raw` je **413 odkazů nesoucích úroveň** na **138 cílových
skillů** **[data]**. Tři nezávislé důkazy, že režim nejde odvodit ani ze
skillu, ani ze sloupce:

**a) Sedm cílových skillů je čtených oběma způsoby** — `Blood Oath`,
`Demonic Mastery`, `Engorge`, `Sigil Death`, `Summon Fenris`, `Summon Grizzly`,
`Summon Spirit Wolf`:

```
Raven.DmgSymPerCalc         -> skill('Summon Grizzly'.blvl)   BASE
Summon Fenris.passivecalc3  -> skill('Summon Grizzly'.ln12)   EFEKTIVNÍ
```

**b) Dvanáct sloupců nese oba režimy** napříč skilly — `calc1`, `calc2`,
`passivecalc1..3`, `aurastatcalc1..4`, `auralencalc`, `sumsk1calc`,
`EDmgSymPerCalc`.

**c) Jeden výraz může nést oba režimy najednou.** Clay Golem `calc1`:

```
(100+(par1*(lvl-1))) * (100 + skill('Golem Mastery'.ln12)
                            + (skill('BloodGolem'.blvl) * skill('BloodGolem'.par8)))/100-100
                                     ^^^^ EFEKTIVNÍ            ^^^^ BASE
```

Ani per-výraz příznak nestačí. **Režim je vlastnost každého jednotlivého
odkazu uvnitř výrazu.**

I dedikovaný synergie sloupec má výjimku: `EDmgSymPerCalc` má 159 odkazů přes
`.blvl`, ale `Summon Spirit Wolf.EDmgSymPerCalc = skill('Summon Grizzly'.ln12)`
počítá synergii z **efektivní** úrovně.

Zúžené tvrzení, které držet lze: Skeleton Mastery (9×) a Golem Mastery (12×)
jsou čtené **výhradně** efektivně. Demonic Mastery ne — má 13× efektivní a 7×
base (v `petmax` a `passivecalc2`), a ty base výjimky přinesl až D2R 3.3.

**Pro necro summony je stav čistý:** skeletoní větev čte Skeleton Mastery vždy
přes `.lvl`, golemové čtou Golem Mastery přes `ln`/`dm` a ostatní golemy přes
`.blvl`. Dělení tam platí — jen se na něj nesmí spoléhat jako na pravidlo, až
model půjde na druidku nebo Vessel skilly.

Rozdíl **není kosmetický**: hráč s 20 body do Blood Golema a +15 z itemů má
pro `.blvl` odkaz pořád 20, ale pro `.lvl` odkaz 35.

### 5.3 Engine to už umí, ale modely to nevyužívají

**[kód]** `EvalContext` drží obojí — `skill_levels_total` i `skill_levels_base`
— a `expr.py` má `skill_lvl()` i `skill_blvl()`. CLI bere `--slvl` i `--blvl`.
Infrastruktura je hotová.

Jenže `EvalContext.lvl` (holý token `lvl` ve výrazech) se plní z **totals**
(`runner.py:75`), a `raise_skeleton.py` i `raise_skeletal_mage.py` čtou
Skeleton Mastery přes `skill_levels_total`. Pro mastery je to správně (5.2).
**Nikde se ale zatím nepočítá nic, co by muselo jít přes `blvl`** — protože
současné tři modely žádnou synergii nepoužívají. Až přijde Clay Golem s Blood
Golem synergií nebo obecné skilly s `EDmgSymPerCalc`, tohle přestane stačit.

### 5.4 Co je potřeba rozhodnout

1. **Reprezentace v schématu.** Minimum je dvojice na skill:
   `character_skill(character_id, skill_id, base_points, effective_level)`.
   Otázka je, jestli `effective_level` **ukládat** (rychlé čtení, riziko
   nekonzistence), nebo **počítat** z `base_points` + `+skills` (vždy správné,
   ale potřebuje kompletní model itemů, který dnes neexistuje —
   `EvalContext.stats` je natvrdo prázdný dict).

2. **Odkud `+skills` vůbec vzít.** Dnes nikde. `+N to all skills`,
   `+N to Necromancer skills`, `+N to Summoning tab`, `+N to konkrétnímu
   skillu` — čtyři různé zdroje s různým rozsahem. Bez modelu itemů se
   efektivní úroveň nedá odvodit; pro P1 se dá obejít tím, že ji uživatel
   zadá ručně (což CLI přes `--slvl`/`--blvl` už umí).

3. **Kde se cap uplatní.** `maxlvl = 20` platí na `base_points`, ne na
   efektivní úroveň (2.5). Schéma by to mělo vynutit `CHECK`em na
   `base_points`, a **žádný cap** na efektivní úrovni.

4. **Který token je default.** Ve výrazech je holý `lvl` = efektivní. Pokud
   se to bude portovat do Javy, tohle je nejsnazší místo, kde udělat tichou
   chybu — `blvl` se od `lvl` liší jedním písmenem a rozdíl se projeví až
   u hráče s itemy.

5. **Otázka, na kterou nemám odpověď z dat:** platí `blvl`/`lvl` rozdíl i pro
   **vlastní** úroveň skillu, nebo jen pro odkazy na cizí skilly? Všech 352
   výskytů `blvl` v datasetu je uvnitř `skill('X'.blvl)`, tedy odkaz na jiný
   skill **[data]**. Holý token `blvl` bez `skill()` se v datasetu nevyskytuje.
   Vypadá to tedy, že vlastní úroveň je vždy efektivní — ale nemám to jak
   potvrdit z dokumentace a nechci to tvrdit jistěji, než to umím doložit.

### 5.5 Co k tomu říká dokumentace

**[guide]** Průvodce rozdíl **explicitně nepopisuje**. K `maxlvl` uvádí
„Maximum **base** level (excluding +N to skills from items)", což potvrzuje,
že rozdíl existuje, ale u `lvl` v calc vzorcích nedefinuje, která z obou
hodnot to je. Sekce SkillDesc.txt neobsahuje k base vs. efektivní úrovni nic.

Rozdíl v 5.1–5.2 je tedy doložený **z dat, ne z dokumentace**. Před
zabetonováním do schématu by stálo za to ověřit ho proti hře na jednom
konkrétním případu (např. Clay Golem s Blood Golem synergií, s itemy a bez).

### 5.6 Změna v D2R 3.3, která se přímo týká tohohle

**[data]** Refresh na 3.3 změnil u tří skillů `petmax` z `.lvl` na `.blvl`:

```
Summon Defiler / Summon Goatman / Summon Tainted
  před:  (skill('Demonic Mastery'.lvl)>=20)?3:((skill('Demonic Mastery'.lvl)>=10)?2:1)
  po:    (skill('Demonic Mastery'.blvl)>=10)?3:((skill('Demonic Mastery'.blvl)>=5)?2:1)
```

Počet petů se nově odvozuje z **hard pointů** místo efektivní úrovně (a prahy
klesly z 20/10 na 10/5). Je to doklad, že Blizzard tenhle rozdíl aktivně ladí
— schéma, které oba pojmy nerozliší, tuhle změnu nedokáže reprezentovat.

---

## 6. Shrnutí pro P1

- **26 sloupců** v `skill` + `skill_param` / `skill_calc` / `skill_calc_ref` /
  `skill_stat_ref`.
- **Priorita č. 1:** `EMin`/`EMax` + `*Lev1..5`. Pátý segment je jediné, co
  nad lvl 28 roste, a je neomezený.
- **`maxlvl` neomezuje výpočet**, jen investované body.
- **Výrazové sloupce musí být `TEXT`**, ne číslo — `petmax` u Raise Skeleton
  je `(lvl < 4) ?lvl:(2+lvl/3)`.
- **`base_points` vs `effective_level` musí být v modelu postavy oddělené**
  od začátku; dodělat to zpětně znamená přepsat každý vzorec.
- **Režim úrovně je vlastnost jednotlivého odkazu, ne skillu ani sloupce ani
  celého výrazu** (5.2). Proto tabulka `skill_calc_ref` s `level_mode` na
  každém odkazu — per-skill flag ani per-výraz příznak to nezachytí.
- **Golemí model není nad lvl 20 důvěryhodný** — `dm` křivka kalibrovaná na
  lvl 1, Clay Golem bez golden testu, a neověřený režim úrovně u `ln`/`dm`.
  Tři problémy, které se sčítají; podrobně v
  [`KNOWN_ACCURACY_GAPS.md`](KNOWN_ACCURACY_GAPS.md), bod 1. Skeletoní větev
  dotčená není.
- `overrides/skills_effects.json` — **hotovo**, smazáno 2026-08-28 (1.3).

### Zdroje

- [D2R Data Guide — legenda typů sloupců](https://locbones.github.io/D2R_DataGuide/)
- [D2R Data Guide — Skills.txt (stránkovaná varianta)](https://d2r-tools.com/data/skills)
- [D2R Data Guide — SkillDesc.txt](https://d2r-tools.com/data/skilldesc)
