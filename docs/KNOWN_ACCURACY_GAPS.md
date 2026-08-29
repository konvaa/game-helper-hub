# Známé mezery v přesnosti výpočtu

Živý dokument (na rozdíl od `AUDIT_2026-08-28.md`, který je datovaný snapshot).
Eviduje místa, kde engine počítá **něco**, ale není ověřené, že to je **správně**.

Řazeno podle rizika pro hlavní scénář aplikace: efektivní úrovně skillu 35–40+.

---

## 1. Baseline sada pro vysoké úrovně není ověřená pravda

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
což **nezávisle potvrzuje náš nález o `blvl`** (synergie z hard pointů, viz
`SKILLS_PROJECTION.md` sekce 5). Dokládá to, že jejich model tenhle rozdíl řeší;
o zdroji dat to neříká nic.

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

## 2. `_dm_curve()` je kalibrovaná na lvl 1 a používá se u Clay Golema

**Stav:** otevřené · **Riziko:** vysoké pro golemy, žádné pro skeletony ·
**Založeno:** 2026-08-28

### Problém

`scripts/summon_engine/expr.py: _dm_curve()` implementuje token `dmAB`:

```
mn + (mx − mn) · lvl / (lvl + K)     kde K = 5.5
```

Vlastní docstring funkce přiznává, jak ta konstanta vznikla:

> "K tuned to 5.5 so that floor() matches in-game tooltip **at lvl1**"

Je to tedy proložení kalibrované **na jediném bodě, a to na úrovni 1** —
nejvzdálenější možné od pásma, kvůli kterému aplikace existuje. Pro lvl 35+
není nijak ověřená. Křivka je asymptotická k `parB`, takže se s rostoucí
úrovní chová „rozumně" (neuteče do nesmyslných hodnot) — což je zrádné,
protože chyba bude tiše malá a věrohodná, ne nápadná.

### Kde se to projeví

| skill | sloupec | co to počítá |
|---|---|---|
| Clay Golem | `aurastatcalc1 = dm34` | **slow %** na zasažené nepřátele |
| Clay Golem | `passivecalc1 = skill('Golem Mastery'.dm34)` | **velocity %** |

Raise Skeleton ani Skeletal Mage `dm` nepoužívají — proto je jejich baseline
tímhle nedotčená.

### Proč to zatím nikdo nezachytil

**Clay Golem nemá ani jeden golden test.** Všech 17 ověřených případů je
Raise Skeleton (8) a Skeletal Mage (9). Model golema je implementovaný,
zaregistrovaný v `MODEL_REGISTRY` a spustitelný přes CLI — ale nic nekontroluje,
co vrací. `dm` křivka je tedy neověřená aproximace bez jakéhokoli testu.

### Co s tím

1. **Než se na golemy postaví DB vrstva nebo UI**, ověřit `dm34` proti hře na
   Clay Golemovi — slow % na lvl 1 (kde je kalibrace), na 20 a na 35+.
   Když se rozejde, `K` není konstanta a křivka potřebuje jiný tvar.
2. **Založit golden sadu pro Clay Golema** i kdyby jen jako regresní —
   současný stav, kdy třetina implementovaných modelů nemá test, je horší
   než neověřená čísla.
3. Zvážit, jestli `dmAB` vůbec **je** asymptotická křivka. Že se dá lvl 1
   proložit hyperbolou, neznamená, že hra počítá takhle; může jít o tabulku,
   segmenty (jako `seg5`) nebo něco jiného. Tohle je otevřená otázka, ne
   ladění konstanty.

---

## 3. Rozdíl base vs. efektivní úroveň není v modelech nikde uplatněný

**Stav:** otevřené · **Riziko:** nízké dnes, vysoké po rozšíření ·
**Založeno:** 2026-08-28

Podrobně v [`SKILLS_PROJECTION.md`, sekce 5](SKILLS_PROJECTION.md). Stručně:
data rozlišují `blvl` (hard pointy) od `lvl` (efektivní) a **`blvl` je v
`skills.txt` dokonce častější** (352 vs. 220 výskytů). Synergie se počítají
z hard pointů, mastery z efektivní úrovně.

Současné tři modely žádnou synergii nepoužívají, takže se to zatím nikde
neprojeví. Clay Golem má ale v `calc1` synergii na Blood Golema přes `.blvl` —
jakmile se ta větev začne používat, rozdíl začne platit.

**Vedlejší nález k baseline sadám — VYŘEŠENO 2026-08-28.** Generátory i
testovací wrappery volaly CLI s `--blvl` rovným `--slvl`, takže případ
`rs40_sm40` deklaroval 40 hard pointů, což je při capu 20 nemožné.

Opraveno ve všech čtyřech souborech (`generate_baseline_*.py`,
`test_*_cases.py`) přes `base_level()` = `min(efektivní, HARD_POINT_CAP=20)`.
Cap odpovídá sloupci `maxlvl` v `skills.txt`, který je 20 pro všechny necro
summon skilly.

Ověřeno, že oprava **nezměnila ani jednu hodnotu**: původních 17 případů dál
prochází a přegenerované `*_highlvl.json` sady jsou bajt po bajtu identické.
To potvrzuje, co bod výše tvrdí — dnes `blvl` žádný model nečte. Význam to
dostane až se synergiemi.
