# Známé mezery v přesnosti výpočtu

Živý dokument (na rozdíl od `AUDIT_2026-08-28.md`, který je datovaný snapshot).
Eviduje místa, kde engine počítá **něco**, ale není ověřené, že to je **správně**.

Řazeno podle rizika pro hlavní scénář aplikace: efektivní úrovně skillu 35–40+.

---

## 1. Golemí model není důvěryhodný nad lvl 20

**Stav:** otevřené · **Riziko:** VYSOKÉ · **Založeno:** 2026-08-28 ·
**Sloučeno:** 2026-08-28

> **Závěr:** golemí větev se nesmí použít jako podklad pro DB vrstvu, UI ani
> pro žádné číslo ukazované uživateli nad lvl 20, dokud se neověří proti
> Maxrollu. Nejde o jeden problém, ale o **tři, které se sčítají** a navzájem
> maskují.
>
> **Skeletoní větev tímhle dotčená není.** Raise Skeleton a Skeletal Mage mají
> matematiku ověřenou proti hře (17 golden případů) a `ln`/`dm` tokeny
> nepoužívají vůbec.

### Problém A — `_dm_curve()` je kalibrovaná na lvl 1

`scripts/summon_engine/expr.py: _dm_curve()` implementuje token `dmAB`:

```
mn + (mx − mn) · lvl / (lvl + K)     kde K = 5.5
```

Vlastní docstring funkce přiznává, jak konstanta vznikla:

> "K tuned to 5.5 so that floor() matches in-game tooltip **at lvl1**"

Proložení na **jediném bodě, a to na úrovni 1** — nejvzdálenější možné od
pásma, kvůli kterému aplikace existuje. Křivka je asymptotická k `parB`, takže
se s rostoucí úrovní chová „rozumně" a neuteče do nesmyslných hodnot. To je
ale zrádné: případná chyba bude tiše malá a věrohodná, ne nápadná.

Je navíc otevřená otázka, jestli `dmAB` **vůbec je** asymptotická křivka. Že
se dá lvl 1 proložit hyperbolou, neznamená, že hra počítá takhle — může jít
o tabulku, segmenty (jako `seg5`) nebo něco jiného. Ladit `K` má smysl až
potom, co se potvrdí tvar.

### Problém B — Clay Golem nemá ani jeden golden test

Všech 17 ověřených případů je Raise Skeleton (8) a Skeletal Mage (9). Model
golema je implementovaný, zaregistrovaný v `MODEL_REGISTRY` a spustitelný přes
CLI — ale **nic nekontroluje, co vrací**. Ani nová `*_highlvl` sada golemy
nepokrývá.

Třetina implementovaných modelů tedy nemá test. To je samo o sobě horší než
neověřená čísla: u baseline sady aspoň víme, že neověřená je.

### Problém C — režim úrovně u `ln`/`dm` je nepodložený předpoklad

`expr.py: skill_var()` vyhodnocuje `skill('X'.lnAB)` a `skill('X'.dmAB)` přes
`ctx.skill_levels_total`, tedy **efektivní** úroveň. To je **rozhodnutí
implementace, ne doložený fakt o hře** — v datech je jen zápis `.ln12`, nikde
není řečeno, jestli se má vyhodnotit z hard pointů nebo z efektivní úrovně.
Viz bod 3, kde je doloženo, že režim je v D2 vlastnost jednotlivého odkazu
a nedá se odvodit z ničeho vyššího.

Týká se to **41 odkazů** (28× `ln`, 13× `dm`) **[data]** — a celá golemí větev
je mezi nimi:

| skill | odkaz | co počítá |
|---|---|---|
| Clay / Blood / Iron / Fire Golem | `calc1` → `Golem Mastery.ln12` | HP % |
| Clay / Blood / Iron / Fire Golem | `passivecalc1` → `Golem Mastery.dm34` | velocity % |
| Clay / Blood / Iron / Fire Golem | `passivecalc2` → `Golem Mastery.ln56` | další bonus |
| Clay Golem | `aurastatcalc1 = dm34` | slow % |

Pokud hra `ln`/`dm` na cizím skillu počítá z hard pointů, jsou **všechny
golemí výpočty nad lvl 20 systematicky nadhodnocené** — a to tím víc, čím víc
`+skills` hráč má, tedy přesně v cílovém scénáři.

### Proč se to sčítá

Každý z těch tří problémů zvlášť by šel obhájit jako drobnost. Dohromady
znamenají, že u golemů **nemáme jak poznat, že je něco špatně**:

- `dm` křivka může být špatná (A), ale
- žádný test to nezachytí (B), protože žádný neexistuje, a
- i kdyby test existoval, mohl by být postavený na špatném režimu úrovně (C),
  takže by chybu potvrdil místo odhalení.

Golemí čísla nad lvl 20 jsou tedy dnes **neověřená, netestovaná a stojící na
neověřeném předpokladu**.

### Co s tím, v tomhle pořadí

1. **Ověřit `ln`/`dm` režim (C) proti Maxrollu** — porovnat Clay Golem HP na
   efektivní úrovni 20 (kde base == efektivní, takže na režimu nezáleží) a na
   35 s hard pointy 20 (kde se oba režimy rozejdou). Rozdíl mezi našimi čísly
   a Maxrollem ukáže, který režim hra používá. **Tohle je první krok** —
   dokud není jasný, nemá smysl ladit `dm` křivku.
2. **Ověřit `dm34` (A)** na Clay Golem slow % — na lvl 1 (kde je kalibrace),
   na 20 a na 35+. Když se rozejde, `K` není konstanta.
3. **Založit golden sadu pro Clay Golema (B)** až po 1 a 2, aby nezabetonovala
   chybný režim. Do té doby ji dělat nemá cenu.

---

## 2. Baseline sada pro vysoké úrovně není ověřená pravda

**Stav:** otevřené · **Riziko:** střední · **Založeno:** 2026-08-28

### Čeho se to týká

| soubor | případů | co je zač |
|---|---|---|
| `scripts/tests/baseline_raise_skeleton_highlvl.json` | 11 | **neověřeno** |
| `scripts/tests/baseline_raise_skeletal_mage_highlvl.json` | 8 | **neověřeno** |
| `scripts/tests/baseline_raise_skeleton.json` | 8 | ověřeno proti hře |
| `scripts/tests/baseline_raise_skeletal_mage.json` | 9 | ověřeno proti hře |

### Co ta čísla jsou a co nejsou

Nová sada `*_highlvl.json` je **výstup současného enginu, zachycený k datu
vzniku**. Není to naměřená pravda z hry. Rozdíl proti původním 17 případům je
zásadní: ty byly porovnané proti skutečnému chování hry, tyhle ne.

Co sada **prokazuje**: že se engine nezmění nepozorovaně. Jako regresní test
(„po refaktoru vychází totéž") plní funkci od první chvíle.

Co sada **neprokazuje**: že ta čísla odpovídají hře. Kdyby měl engine ve
vysokých úrovních systematickou chybu, tahle sada ji **zabetonuje** místo
odhalení — bude vesele zelená a bude chybu chránit.

Provedená kontrola byla jen **vnitřní konzistence**: všech 9 „hell" případů
Raise Skeletonu sedí s nezávislým ručním přepočtem podle vzorců
(`seg5` + `Param2..5` + base staty z `monsters_base`). To potvrzuje, že engine
implementuje to, co si o něm myslíme — ne že je ten model správný.

### Jak je ověřit proti realitě

Seřazeno od nejspolehlivějšího.

**a) Maxroll D2Planner — primární zdroj.**
[maxroll.gg/d2/d2planner](https://maxroll.gg/d2/d2planner) zobrazuje damage
přesně a **bylo to opakovaně ověřené proti hře**. To z něj dělá nejspolehlivější
dostupný referenční bod, spolehlivější než odečítání tooltipu ve hře.

Dvě věci k němu ale zůstávají **nezjištěné** — nedokázal jsem je ověřit,
protože planner je pro automatické načtení blokovaný přes `robots.txt`:

1. **Přijímá efektivní úrovně nad 20?** Planner pracuje s investovanými body
   (cap 20) plus výbavou; jestli se `+skills` z itemů promítnou do zobrazeného
   damage summonů, je potřeba ověřit ručně. Bez toho je pro pásmo 29+
   nepoužitelný.
2. **Jaká je jeho nezávislost?** Nepodařilo se zjistit, jestli čte stejné
   herní `.txt` soubory jako my, nebo má vlastní model.
   **Pokud sdílíme zdroj, platí stejná výhrada jako u palmdabomb: shoda
   vyloučí chybu v naší aritmetice, ne chybu ve společné interpretaci dat.**
   Pokud má vlastní model postavený na měření ve hře, je to plnohodnotné
   nezávislé ověření a výhrada odpadá.

Nepřímá indicie k jejich modelu: stránka
[Damage Calculation](https://maxroll.gg/d2/resources/damage-calculation) uvádí,
že „only Skill Points allocated to the Synergy Skill actually applies a bonus" —
což odpovídá **převažujícímu** případu v datech (viz bod 3 níže — univerzální
pravidlo to není, sami píšou „for the majority of Skills" a zmiňují výjimky).
Dokládá to, že jejich model rozdíl base/efektivní řeší; o zdroji dat to
neříká nic.

**b) In-game tooltip — jen pro Raise Skeleton.**
Tooltip Raise Skeletonu zobrazuje `Skeleton Damage` a `Skeleton Life` přímo.
Postup: 20 hard pointů, pak přidávat `+skills` itemy a odečítat na efektivních
úrovních 23, 28, 29, 35, 40. **Úrovně 28 a 29 jsou nejdůležitější** — je mezi
nimi hranice segmentu, takže případná chyba v `seg5` se projeví právě tam.
Skeleton Mastery při měření nechat na 0 (případ `rs40_sm00_hell` je přesně
pro tohle).

> **Pro Skeletal Mage je tahle metoda nepoužitelná.** Hra nikde nezobrazuje
> missile damage `necromage` jednotek, takže elementální hodnoty
> (poison/cold/fire/lightning) z tooltipu odečíst nejdou. RSM sada se musí
> ověřit přes Maxroll (a) nebo přes palmdabomb (d).

**c) Rozdílové měření místo absolutního.**
Nemusí se měřit celá tabulka. Stačí ověřit **sklon** pátého segmentu: odečíst
hodnotu na lvl 29 a na lvl 30 a zkontrolovat, že rozdíl odpovídá `EMinLev5`
(u Raise Skeletonu = 4 flat damage před aplikací damage %). Dvě měření místo
jedenácti, a testují přesně to, co je na vysokých úrovních nové.

**d) palmdabomb — křížová kontrola.**
[D2R Static Tools — Necromancer Summons Calculator](https://palmdabomb.github.io/D2RStaticTools/html/necroSummons.html)
pokrývá Raise Skeleton, Skeletal Mage i všechny golemy a **explicitně uvádí
jako zdroj herní soubory včetně `MonStats.txt`** — tedy tentýž zdroj, ze
kterého čteme my. Shoda proto vyloučí chybu v naší aritmetice, ne chybu ve
společné interpretaci dat.
Jestli přijímá úrovně nad 20, **se mi rovněž nepodařilo zjistit** — WebFetch
převádí stránku na text a interaktivní ovládací prvky v něm nejsou vidět.
Je to otázka na půl minuty ručně: otevřít stránku a zkusit zadat 40.

### Připravená srovnávací tabulka

Aby ověření nevyžadovalo dohledávání, tady jsou naše hodnoty k odečtení.
Sloupec „reference" je k vyplnění.

**Raise Skeleton** (hell; `sm=0` izoluje samotný Raise Skeleton):

| případ | RS | SM | naše HP | náš dmg | reference HP | reference dmg |
|---|---|---|---|---|---|---|
| `rs28_sm28_hell` | 28 | 28 | 791 | 261–264 | | |
| `rs29_sm29_hell` | 29 | 29 | 820 | 284–287 | | |
| `rs35_sm35_hell` | 35 | 35 | 994 | 443–447 | | |
| `rs40_sm40_hell` | 40 | 40 | 1139 | 599–603 | | |
| `rs40_sm00_hell` | 40 | 0 | 819 | 312–315 | | |

Dvojice 28 → 29 je nejcennější: rozdíl v `rs_internal_flat` musí být přesně
**+4** (38 → 42).

**Skeletal Mage** (hell; elementální dmg řídí RSL, ne přímo úroveň skillu):

| případ | RSM | SM | RSL | naše HP | fire | cold | reference |
|---|---|---|---|---|---|---|---|
| `rsm28_sm28_hell` | 28 | 28 | 41 | 654 | 229–233 | 208–210 | |
| `rsm29_sm29_hell` | 29 | 29 | 42 | 674 | 238–242 | 217–219 | |
| `rsm35_sm35_hell` | 35 | 35 | 51 | 796 | 319–323 | 298–300 | |
| `rsm40_sm40_hell` | 40 | 40 | 59 | 898 | 391–395 | 370–372 | |
| `rsm40_sm00_hell` | 40 | 0 | 19 | 578 | 55–59 | 37–39 | |

Poslední řádek je kontrolní: s `SM=0` klesne RSL na 19, tedy **mimo pátý
segment**. Pokud sedí ostatní čtyři a tenhle ne (nebo naopak), ukazuje to
přímo na `seg5`.

Pozor na past: `rsm45_sm20_hell` má RSL **41**, stejně jako `rsm28_sm28_hell` —
elementální damage u obou vychází identicky (fire 229–233). Není to chyba;
RSL závisí na `SM + (lvl−2)/2`, takže různé kombinace dají tutéž hodnotu.

### Vědomé rozhodnutí: sada není napojená na `pytest`

Nová sada **záměrně nemá** testovací wrapper a `pytest scripts/tests/` ji
nespouští — pořád běží původních 17 případů. Důvod: dokud čísla nejsou
ověřená, zelený test by dodával falešnou jistotu.

Napojení je jednořádková změna (zkopírovat `test_raise_skeleton_cases.py`
a přepsat cestu k baseline). Udělat by se to mělo **až** po ověření podle
bodu a) nebo b) — nebo dřív, ale s jasným označením, že jde o regresní,
nikoli akceptační test.

---

## 3. Base vs. efektivní úroveň se řídí per-odkaz, ne pravidlem

**Stav:** zmapováno · **Riziko:** nízké dnes, vysoké po rozšíření ·
**Založeno:** 2026-08-28 · **Zpřesněno:** 2026-08-28

### Oprava dřívějšího tvrzení

Starší verze tohoto dokumentu i `SKILLS_PROJECTION.md` tvrdily pravidlo
„synergie z hard pointů, mastery z efektivní úrovně". **To pravidlo neplatí.**
Je to převažující případ, ne zákonitost — a analýza všech 429 skillů to
vyvrací hned několikrát.

### Co data skutečně říkají

Napříč `skills_raw` je **413 odkazů nesoucích úroveň** na **138 různých
cílových skillů**. Rozložení režimů:

| forma zápisu | výskytů | režim |
|---|---|---|
| `skill('X'.blvl)` | 352 | base (hard pointy) |
| `skill('X'.lnAB)` | 28 | efektivní |
| `skill('X'.lvl)` | 20 | efektivní |
| `skill('X'.dmAB)` | 13 | efektivní |

**Tři nezávislé důkazy, že jde o vlastnost odkazu, ne skillu ani sloupce:**

**1. Sedm cílových skillů je čtených oběma způsoby.**
`Blood Oath`, `Demonic Mastery`, `Engorge`, `Sigil Death`, `Summon Fenris`,
`Summon Grizzly`, `Summon Spirit Wolf`. Nejzřetelnější je vlčí trojice:

```
Raven.DmgSymPerCalc            -> skill('Summon Grizzly'.blvl)   BASE
Summon Fenris.passivecalc3     -> skill('Summon Grizzly'.ln12)   EFEKTIVNI
```

Tentýž cílový skill, dva různé režimy. Žádný per-skill flag tohle nezachytí.

**2. Dvanáct sloupců nese oba režimy** napříč skilly — včetně `calc1`,
`passivecalc1..3`, `aurastatcalc1..4`, `auralencalc` a `sumsk1calc`.
Sloupec tedy režim neurčuje.

**3. Jeden výraz může obsahovat oba režimy najednou.** Clay Golem `calc1`:

```
(100+(par1*(lvl-1))) * (100 + skill('Golem Mastery'.ln12)
                            + (skill('BloodGolem'.blvl) * skill('BloodGolem'.par8)))/100-100
                                     ^^^^ EFEKTIVNI              ^^^^ BASE
```

Ani per-výraz příznak tedy nestačí — režim je vlastnost **každého jednotlivého
odkazu uvnitř výrazu**.

### Výjimka i v synergie-sloupcích

`EDmgSymPerCalc` je dedikovaný synergie sloupec: 159 odkazů přes `.blvl`.
**Ale jeden ne:**

```
Summon Spirit Wolf.EDmgSymPerCalc = skill('Summon Grizzly'.ln12)
```

Synergie počítaná z **efektivní** úrovně. Jediná výjimka ze 160, ale existuje —
takže „synergie vždy z hard pointů" je nepravda. (Druhá nestandardní hodnota,
`Abyss.EDmgSymPerCalc`, odkazuje na `.par8`, což úroveň nenese vůbec.)

### Sub-vzorec, který skutečně drží

Tvrzení o mastery se dá vyslovit jen ve zúžené podobě:

| mastery skill | efektivní | base | poznámka |
|---|---|---|---|
| Skeleton Mastery | 9 | **0** | režim jednotný |
| Golem Mastery | 12 | **0** | režim jednotný |
| Demonic Mastery | 13 | **7** | výjimky v `petmax` a `passivecalc2` |

Skeleton a Golem Mastery jsou tedy vždy efektivní. **Demonic Mastery ne** — a
ty base výjimky vznikly refreshem na D2R 3.3, který přepsal `petmax` u Summon
Defiler / Goatman / Tainted z `.lvl` na `.blvl`. Blizzard režim aktivně mění
per-odkaz, takže i tenhle sub-vzorec je jen dnešní stav dat, ne zákonitost.

Obráceně to neplatí vůbec: **8 z 11 cílů čtených efektivně nejsou mastery**
(`Blood Oath`, `Summon Spirit Wolf`, `Shape Shifting`, `Summon Defiler`,
`Summon Fenris`, `Summon Grizzly`, `Engorge`, `Sigil Death`).

### Necromancerovy summony — nejbližší cíl

Dobrá zpráva: v naší nejbližší oblasti je stav čistý a konzistentní.

| skill | odkaz | režim |
|---|---|---|
| Raise Skeleton | Skeleton Mastery `.lvl` (4×) | efektivní |
| Raise Skeletal Mage | Skeleton Mastery `.lvl` (3×) | efektivní |
| Revive | Skeleton Mastery `.lvl` (2×) | efektivní |
| Clay / Blood / Iron / Fire Golem | Golem Mastery `.ln12`/`.ln56`/`.dm34` | efektivní |
| Clay / Blood / Iron / Fire Golem | ostatní golemové `.blvl` | base |

Pro necro summony tedy mastery/synergie dělení **platí**. Jen se na něj nesmí
spoléhat jako na pravidlo, až se model rozšíří na druidku nebo Vessel skilly —
tam se rozpadne.

### Poznámka k implementaci: `ln`/`dm` tokeny

`expr.py` vyhodnocuje `skill('X'.lnAB)` i `.dmAB` přes efektivní úroveň. To je
rozhodnutí implementace, ne doložený fakt — a vzhledem k tomu, co je výše
doloženo o per-odkaz režimu, je to předpoklad bez opory v datech.

Týká se 41 odkazů včetně celé golemí větve. **Rozvedeno v bodu 1, problém C**,
kde je to spolu s dalšími dvěma problémy důvodem, proč golemí model není nad
lvl 20 důvěryhodný.

### Stav v kódu

`EvalContext` drží `skill_levels_total` i `skill_levels_base`, `expr.py` má
`skill_lvl()` i `skill_blvl()`, CLI bere `--slvl` i `--blvl`. Infrastruktura
je hotová a rozdíl umí.

Současné tři modely ale žádný `blvl` odkaz nevyhodnocují — Raise Skeleton,
Skeletal Mage ani Revive ho v odkazech na Skeleton Mastery nemají. První, kdo
na to narazí, je Clay Golem se synergií na Blood Golema.

### Vedlejší nález k baseline sadám — VYŘEŠENO 2026-08-28

Generátory i testovací wrappery volaly CLI s `--blvl` rovným `--slvl`, takže
případ `rs40_sm40` deklaroval 40 hard pointů, což je při capu 20 nemožné.

Opraveno ve všech čtyřech souborech (`generate_baseline_*.py`,
`test_*_cases.py`) přes `base_level()` = `min(efektivní, HARD_POINT_CAP=20)`.
Cap odpovídá sloupci `maxlvl` v `skills.txt`, který je 20 pro všechny necro
summon skilly.

Ověřeno, že oprava **nezměnila ani jednu hodnotu**: původních 17 případů dál
prochází a přegenerované `*_highlvl.json` sady jsou bajt po bajtu identické.
