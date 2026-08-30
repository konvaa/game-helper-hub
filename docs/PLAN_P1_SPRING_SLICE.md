# P1 — Spring Boot slice: plán

**Stav dokumentu:** v5 — rozhodnutí v sekci 9, kroky 1–3 hotové.
Průběžné poznámky ke kódu: [`BACKEND_ZAPISNIK.md`](BACKEND_ZAPISNIK.md).
**Datum:** 2026-08-29
**Rozsah:** podle `AUDIT_2026-08-28.md` sekce 5.4, osekaná varianta.

---

## 0. Co tenhle dokument je

Plán práce na P1, plus zdůvodnění každého architektonického rozhodnutí.
Je psaný tak, aby se dal použít jako příprava na pohovor: u každého bodu je
**proč**, ne jen **co**, a u věcí, které jsou v Javě idiomatické jinak než
v Pythonu, je rozdíl vypíchnutý zvlášť (sekce označené **[Python → Java]**).

---

## 1. Zjištění, která mění zadání

Než se pustíme do plánu, tři věci, které jsem ověřil a které je potřeba znát.

### 1.1 Ani jedno z prostředí, kde běžím, neumí sestavit Javu

| prostředí | java | javac | maven | docker | Maven Central |
|---|---|---|---|---|---|
| Cowork Linux VM (má připojenou složku `F:\Projekty\Game Helper`) | JRE | **ne** | **ne** | **ne** | **blokované (403)** |
| Cowork cloud kontejner (nevidí tvoje soubory) | JDK 21 | ano | 3.9.11 | ano | **blokované (403)** |

Důsledek, se kterým je potřeba počítat: **kód, který napíšu, je do tvého
spuštění `mvn test` neověřený.** Nemůžu ho zkompilovat ani jednou. To mění
způsob práce — místo „napíšu ti celý slice" jedeme po malých přírůstcích,
kde po každém spustíš build ty a chybové hlášky řešíme spolu.

Prakticky to není špatně: kompilátor a testy jsou zpětná vazba, kterou při
učení chceš dostávat ty, ne já.

**Co potřebuješ mít na Windows:** JDK 21, Docker Desktop. Maven ne — do repa
dáme Maven Wrapper (`mvnw`/`mvnw.cmd`), který si stáhne správnou verzi sám.
Proč wrapper: každý, kdo repo naklonuje (včetně náboráře), spustí build bez
instalace Mavenu a s **tou samou** verzí, kterou používáš ty.

### 1.2 „17/17" ve slice nebude pravda

Akceptační kritérium zmiňuje větu do README *„17/17 shodných výsledků"* a
zároveň *8 baseline případů Raise Skeletonu*. Těch 17 = 8 (Raise Skeleton) +
9 (Skeletal Mage). Slice portuje **jen Raise Skeleton**, takže poctivá věta
zní:

> „8/8 shodných výsledků s referenční Python implementací (Raise Skeleton)."

Na 17/17 se dostaneme, až přibude Skeletal Mage — což je první věc po slice
(je to jen druhý model, ne rozšíření architektury). Do README napíšeme
8/8 a upravíme, až to bude platit.

### 1.3 Vzorec Raise Skeleton jsem si ověřil — je to osm řádků aritmetiky

Rekonstruoval jsem výpočet jen z DB sloupců (bez enginu) a prohnal ho
baseline případem `rs20_sm11`:

```
monstr necroskeleton / hell:  max_hp=42  a1_min_d=1  a1_max_d=2  ac=6  a1_th=6
Raise Skeleton:               EMin=0  EMinLev1..5 = 0,1,2,3,4
                              Param2=50 (HP%/lvl)  Param3=7 (dmg%/lvl)
                              Param4=15 (AR)       Param5=15 (DEF)

flat   = seg5(20) + sm*2            = 16 + 22 = 38
hp     = floor(42 + 42*((20-3)*50)/100 + 11*8)         = 487
min    = floor((1 + 38) * (1 + ((20-3)*7)/100))        = 85
max    = floor((2 + 38) * (1 + ((20-3)*7)/100))        = 87
def    = floor(6 + (20+11)*15)                         = 471
ar     = floor(6 + (20+11)*15)                         = 471
count  = 20<4 ? 20 : (int)(2 + 20/3.0)                 = 8
```

Sedí na všech šest hodnot baseline. **Potvrzuje to, že `expr.py` do slice
opravdu není potřeba** — všechno je čtení sloupců plus aritmetika. Jediná
netriviální funkce je `seg5`, a ta má vlastní uzavřený tvar:

```
seg5(lvl) = EMin
          + min(7, max(0, lvl-1))  * EMinLev1     // segment 2–8
          + min(8, max(0, lvl-8))  * EMinLev2     // segment 9–16
          + min(6, max(0, lvl-16)) * EMinLev3     // segment 17–22
          + min(6, max(0, lvl-22)) * EMinLev4     // segment 23–28
          +         max(0, lvl-28) * EMinLev5     // segment 29+, bez stropu
```

Kontrolní hodnoty pro Raise Skeleton (shodné s `SKILLS_PROJECTION.md` 2.2):
lvl 16 → 8, lvl 20 → 16, lvl 29 → 42, lvl 40 → 86, lvl 50 → 126.

---

## 2. Umístění a tvar projektu

### 2.1 Jeden Maven modul, ne multi-module

Multi-module Maven (parent POM + agregátor) přidává složitost, kterou čtyři
endpointy nezaplatí. U pohovoru se lépe obhajuje čistá struktura balíčků
v jednom modulu než nafouknutý strom modulů.

### 2.2 Balíčky podle funkce, ne podle vrstvy

```
cz.libor.gamehelper
├── GameHelperApplication.java
├── common/          ProblemDetail advice, sdílené výjimky
├── config/          OpenAPI, Jackson
├── skill/           Skill, SkillParam, SkillRepository, SkillService,
│                    SkillController, dto/
├── monster/         Monster, MonsterStat, ... 
├── summon/          Seg5, RaiseSkeletonCalculator, SummonController, dto/
└── ingest/          DatasetImporter (JSON → DB)
```

**Proč ne `controller/`, `service/`, `repository/`:** vrstvové balíčky
rozprostřou jednu funkci do čtyř adresářů a nutí dělat všechno `public`
(protože se to volá napříč balíčky). Funkční balíčky drží změnu na jednom
místě a dovolí `package-private` viditelnost — `SkillRepository` nemusí být
vidět odnikud jinud než ze `skill/`. Je to i to, co dnes dělá většina
moderních Spring kódů.

### 2.3 Kam v repozitáři

Rozhodnuto: **`backend/`** v současném repozitáři (`F:\Projekty\Game Helper\backend\`).

Kořen repa už drží Python projekt (`conftest.py`, `requirements.txt`,
`scripts/`); Maven projekt v kořeni by byl zmatek.

Monorepo vs. samostatné repo bylo zvážené a rozhodnuté ve prospěch monorepa — viz sekce 9, řádek B.

---

## 3. Závislosti a proč každá

`pom.xml`, **Spring Boot 3.5.16**, Java 21 (LTS).

**Proč 3.5, když aktuální hlavní řada je 4.1.1** (ověřeno v Maven Central):
3.5 je poslední řada 3.x, pořád živená opravami, a veškerá dokumentace
a odpovědi na Stack Overflow na ni sedí — u 4.x bys u poloviny dotazů řešil,
co z textu psaného pro 3.x ještě platí. Ekosystém (springdoc, Flyway,
Testcontainers) je na 3.5 usazený. Pro pohovor je rozhodující, že umíš JPA,
Flyway, REST a testy, ne číslo v `pom.xml`. Přechod na 4.x je později změna
verze plus bump springdocu (2.8.x → 3.x).

| závislost | proč |
|---|---|
| `spring-boot-starter-web` | REST. Ne WebFlux — pod tím je stejně blokující JDBC, reaktivní stack by přinesl jen složitost |
| `spring-boot-starter-data-jpa` | entity + repozitáře. Viz 3.1 |
| `spring-boot-starter-validation` | `@Valid` na compute requestu |
| `postgresql` (runtime) | JDBC driver |
| `flyway-core` + `flyway-database-postgresql` | migrace. Viz 4.1 |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI na `/swagger-ui` |
| `spring-boot-starter-test` | JUnit 5, AssertJ, MockMvc, Mockito |
| `testcontainers` + `junit-jupiter` + `postgresql` | **volitelné**, viz 7.4 |

Jackson se přidá sám se `starter-web` — použije ho i importer.

### 3.1 Proč JPA a ne JdbcTemplate

Pro tenhle datový model je to hraniční rozhodnutí a stojí za to umět
obhájit obě strany:

- **Pro JPA:** vztah `skill` → `skill_param` (1:N) je přesně to, na co je
  ORM. A hlavně — JPA/Hibernate je v inzerátech na Java pozice prakticky
  vždy, JdbcTemplate skoro nikdy. Cíl projektu je doklad znalostí.
- **Proti JPA:** naše data jsou read-only, načtená jednou importem. Na
  čtení bez zápisu je JPA overhead (persistence context, dirty checking,
  lazy proxy) navíc.

**Rozhodnutí: JPA**, ale s dvěma pravidly, která ty nevýhody odstraní:
1. Read endpointy používají **projekce** (interface nebo DTO projekce),
   ne načtení celé entity. Tím se vyhneme N+1 na `skill_param`.
2. Servisní metody čtení jsou `@Transactional(readOnly = true)` — Hibernate
   pak vypne dirty checking a nedělá flush.

**[Python → Java]** V Pythonu si data načteš jako dict a je to. V Javě mezi
tebou a řádkem stojí persistence context: objekt načtený v transakci je
„managed", po jejím konci „detached", a sáhnutí na lazy kolekci na detached
objektu skončí `LazyInitializationException`. Většina záhadných chyb
začátečníků v JPA je tohle. Proto DTO — viz 5.2.

---

## 4. Schéma

### 4.1 Proč Flyway a ne `hibernate.ddl-auto`

Toto je téměř jistá otázka u pohovoru.

`ddl-auto=update` nechá schéma vygenerovat Hibernate z entit. Problémy:

1. **Není deterministické.** Výsledek závisí na verzi Hibernate a na
   dialektu. Upgrade knihovny ti může tiše změnit typ sloupce.
2. **Neumí `ALTER` ani `DROP`.** Přidat sloupec umí; přejmenovat, zúžit typ
   nebo zahodit sloupec ne. Schéma se časem rozjede a nikdo neví jak.
3. **Neumí věci, které v našem schématu potřebujeme:** `CHECK` omezení
   (`level_mode IN ('base','effective','none')`, `base_points <= 20`),
   částečné a trigramové indexy, `COMMENT ON COLUMN`.
4. **Neumí datové migrace.** „Rozděl sloupec `name` na `key` + `display`
   a přepočítej data" je SQL, ne anotace.
5. **Není to review-ovatelné.** Schéma není v gitu, je to vedlejší efekt.

Flyway: očíslované SQL soubory v `src/main/resources/db/migration/`
(`V1__baseline.sql`, `V2__...`), aplikované v pořadí, evidované v tabulce
`flyway_schema_history`. Schéma je artefakt v gitu, který jde číst v diffu.

**Dvojice, která to spojí:** `spring.jpa.hibernate.ddl-auto=validate`.
Flyway schéma vytvoří, Hibernate ho při startu **ověří** proti entitám a
při rozjezdu (chybějící sloupec, jiný typ) aplikace nenastartuje. Tohle je
idiomatická kombinace: Flyway vlastní schéma, Hibernate hlídá shodu.

### 4.2 Co je minimum pro čtyři endpointy

Z projekce v `SKILLS_PROJECTION.md` (`skill` 26 sloupců + `skill_param`,
`skill_calc`, `skill_calc_ref`, `skill_stat_ref`) potřebuje slice tohle:

**Rozdělení do migrací** (viz `BACKEND_ZAPISNIK.md`, krok 1): `V1` vzniklo
už v kroku 1 a obsahuje jen `game` + `dataset_version`; zbytek přijde v `V2`
v kroku 2. Důvod je pravidlo Flyway o kontrolních součtech — už aplikovaná
migrace se needituje. Dohromady dávají „baseline" popsaný v téhle tabulce:

**`V1` + `V2`:**

| tabulka | proč je ve slice |
|---|---|
| `game` | jeden řádek `d2r`. Viz 4.3 |
| `dataset_version` | provenience z `index.json` (`excel_dir`, čas importu) |
| `skill` | 26 sloupců podle projekce |
| `skill_param` | `Param1..12`; compute čte `par2..par5`, detail endpoint je zobrazí i s popisy |
| `monster` | identita monstra |
| `monster_stat` | staty per obtížnost |

**Co ve slice není a proč to nevadí:**
`skill_calc`, `skill_calc_ref`, `skill_stat_ref`. Žádný ze čtyř endpointů je
nečte a naplnit `skill_calc_ref.level_mode` znamená napsat parser tokenů ve
výrazech — což je přesně ta práce, kterou zadání vylučuje.

Přidat je později přes `V2__` **není** „přepisování struktury" — přesně
k tomu migrace jsou. Nebezpečné je jen to, co se dá špatně **naplnit**:
kdybychom teď dali `level_mode` jako příznak na `skill`, museli bychom
později přepsat i data. Návrh z projekce (samostatná tabulka, `level_mode`
na každém odkazu) se tímhle neruší — jen se odkládá jeho vytvoření.

Zvažovali jsme vzít `skill_calc` hned (je to čisté zkopírování sloupců
`calc1`, `aurastatcalc1`, `petmax`, … do `(slot, expr)`, žádný parser).
**Rozhodnuto ne** — ve slice pro ni není konzument a nenaplněná tabulka je
jen otázka navíc při review. Přijde přes `V2__`, až ji `GET /api/skills/{key}`
bude opravdu vracet.

### 4.3 Proč `game` už teď, když je jedna hra

`skill.game_id` od prvního dne. Přidat rozlišovací sloupec později znamená
přepsat každé unikátní omezení (`UNIQUE (skill_key)` → `UNIQUE (game_id,
skill_key)`), každý index a každý dotaz. Teď je to jedna tabulka a jeden
FK; potom je to migrace přes celé schéma. Dlouhodobá vize projektu je
explicitně víc her, takže to není spekulace.

### 4.4 Rozhodnutí o typech — a proč

| rozhodnutí | proč |
|---|---|
| `petmax_expr`, `calc1_expr`, `aurastatcalc1_expr` → **`TEXT`** | jsou to **výrazy**, ne čísla: `petmax = '(lvl < 4) ?lvl:(2+lvl/3)'`. Číselný typ by import shodil |
| prázdný string ze zdroje → **`NULL`** při importu | v `skills_raw` je „prázdno" prázdný string. `Integer.parseInt("")` vyhodí výjimku, a `WHERE emin IS NULL` vs `= ''` je jinak dvojznačné. Python to řeší v `_to_int`/`_to_float` — importer musí dělat totéž |
| `id BIGSERIAL` PK + `skill_key TEXT` s `UNIQUE (game_id, skill_key)` | surrogate klíč dá třem podřízeným tabulkám úzký stabilní FK; `skill_key` je business klíč a **ten je v URL**, protože je čitelný a stabilní. `*Id` z exportu **není** klíč — projekce 3.1.A říká, že je to komentářový sloupec a při změně pořadí řádků se posune. Uložíme ho jako `ext_id` pro dohledatelnost |
| `skill_key` = normalizovaný (`raise_skeleton`), `name` = surový (`Raise Skeleton`) | normalizace musí být **bit-shodná** s `normalize_key()` v Pythonu, jinak se rozejdou baseline testy. Je to 4 řádky: trim, `-` → mezera, sjednotit mezery, mezera → `_`, lowercase |
| `EMinLev1..5` `INTEGER NULL` | malá čísla, ale `INTEGER` nic nestojí a nemusím řešit rozsah |
| `maxlvl` uložit, ale **nepoužít jako strop výpočtu** | `SKILLS_PROJECTION` 2.5: `maxlvl=20` je strop investovaných bodů, ne efektivní úrovně. Celá aplikace existuje kvůli úrovním 35–40+ |
| `difficulty TEXT` + `CHECK IN ('normal','nightmare','hell')`, ne PG `ENUM` | `ALTER TYPE ... ADD VALUE` je v Postgresu nepříjemný (ve starších verzích nejde v transakci, hodnotu nelze odebrat). `CHECK` dá stejnou záruku a mění se běžnou migrací |
| v Javě `@Enumerated(EnumType.STRING)`, **nikdy `ORDINAL`** | `ORDINAL` uloží 0/1/2 podle pořadí v enumu. Kdokoli později prohodí pořadí konstant → tichá záměna dat v celé DB. Klasická past |

### 4.5 Fulltext v názvu

Pro slice: `WHERE lower(s.name) LIKE lower(concat('%', :q, '%'))` + index
`GIN (lower(name) gin_trgm_ops)` (rozšíření `pg_trgm`).

**Proč ne `tsvector`/`to_tsquery`:** plnotextové vyhledávání v Postgresu
pracuje se **slovy** a jejich kořeny — je skvělé na odstavce textu, ale
neumí infix („skel" nenajde „Skeleton", protože to není prefix slova ani
lemma). Na 429 krátkých názvů je trigramový index přesně ten nástroj.
Zároveň je to hezká odpověď na otázku „jak jste řešil hledání" — ukazuje,
že jsi vybíral, ne že jsi vzal první `LIKE`.

---

## 5. API

### 5.1 Kontrakty

```
GET /api/skills?charClass=nec&q=skel&page=0&size=20&sort=name,asc
    200 → PagedResponse<SkillSummaryDto>
          { content:[{key,name,charClass,reqLevel,maxLevel}],
            page, size, totalElements, totalPages }

GET /api/skills/{key}          200 → SkillDetailDto | 404 → ProblemDetail
GET /api/monsters/{id}         200 → MonsterDto     | 404 → ProblemDetail

POST /api/summons/raise-skeleton/compute
     { "raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "hell" }
     200 → { input, monsterId, final:{hp,physMin,physMax,defense,attackRating,count},
             breakdown:[{label,value}, ...] }
     400 → ProblemDetail (validace)
```

**Proč `POST` na compute a ne `GET`:** audit to má takhle, a obhájit se to
dá tím, že jde o příkaz s tělem, ne o adresovatelný zdroj. Ale měl bys znát
i protiargument: `GET /api/summons/raise-skeleton?rs=20&sm=11&difficulty=hell`
je idempotentní, cache-ovatelné a dá se poslat odkazem. U pohovoru je dobré
říct „vybral jsem POST kvůli tělu requestu, ale GET by tady taky obstál
a byl by cacheovatelný" — to zní jinak než „tak se to dělá".

**Proč vlastní `PagedResponse` a ne přímo Springový `Page`:** Spring sám
při serializaci `PageImpl` loguje varování, že jeho JSON tvar není
stabilní napříč verzemi. Vlastní record je pět řádků a kontrakt API pak
nedrží knihovna.

**Validace úrovní:** `@Min(1) @Max(99)` na obě úrovně. **Ne `@Max(20)`** —
20 je strop investovaných bodů, ne efektivní úrovně (4.4). Do
`@Schema(description=...)` to napíšeme, ať je to v Swaggeru vidět.

### 5.2 Proč DTO a ne entita v response

Druhá téměř jistá otázka u pohovoru. Pět důvodů, seřazených podle toho, jak
často reálně kousnou:

1. **Lazy loading.** Jackson serializuje entitu mimo transakci, sáhne na
   lazy kolekci → `LazyInitializationException`. Když ji necháš eager,
   serializuje se ti celý graf (`skill` → 12 parametrů → …), takže seznam
   429 skillů vygeneruje stovky dotazů (N+1).
2. **Cyklus.** Obousměrný vztah (`skill.params` ↔ `param.skill`) se
   serializuje donekonečna, dokud nespadne na `StackOverflowError`.
3. **Kontrakt.** Entita je tvar databáze. Když ji vystavíš, každé
   přejmenování sloupce je breaking change pro klienta. DTO je smlouva,
   kterou měníš vědomě.
4. **Tvar podle endpointu.** Seznam chce pět polí, detail třicet. S entitou
   to jde jen přes `@JsonView` a podobné berličky.
5. **Bezpečnost.** Co je v entitě, to jde ven — včetně polí, která tam
   nechceš.

**[Python → Java]** V Pythonu bys prostě postavil dict s tím, co chceš
vrátit — a nikdo tomu neříká DTO. V Javě je to totéž, jen to má typ:
`record SkillSummaryDto(String key, String name, ...)`. Ten `record`
(Java 16+) je neměnný, má vygenerovaný konstruktor, `equals`, `hashCode`
a `toString` — pro DTO je to přesně ono. Rozdíl proti Pythonu není
filozofický, je v tom, že Java má lazy proxy a kompilovaný kontrakt, takže
oddělení něco reálně řeší.

Mapování entita → DTO: ruční mapper (statická metoda / malá `@Component`
třída). **Ne MapStruct ani ModelMapper** — pro pár DTO je generátor
zbytečná závislost a u pohovoru chceš umět ukázat, že to mapování rozumíš,
ne že za tebe něco vygenerovalo anotací.

### 5.3 Chyby: `ProblemDetail`, ne vlastní error DTO

`@RestControllerAdvice` + `ProblemDetail` (Spring 6 / RFC 9457,
`application/problem+json`):

```json
{ "type":"about:blank", "title":"Not Found", "status":404,
  "detail":"Skill 'raise_skeletn' not found", "instance":"/api/skills/raise_skeletn" }
```

**Proč:** je to standard, Spring ho má vestavěný (nemusím psát vlastní
record), a `ResponseEntityExceptionHandler` už umí převést i validační
chyby. Vlastní `ApiError` třída by dělala to samé hůř.

---

## 6. Import dat

Zdroj: `data/diablo2/resurrected/helpers/generated/` — pro slice stačí
`index.json` (provenience), `skills_raw.json` (3,5 MB, 429×322) a
`monsters_base.json` (0,9 MB, 752 monster). `missiles_raw.json` (3,6 MB)
slice nepotřebuje.

Návrh: **Java importer**, `ingest/DatasetImporter`, Jackson + JDBC batch
insert, spuštěný `ApplicationRunner`em, **idempotentní** — když už v
`dataset_version` sedí řádek pro stejný `excel_dir` + hash, přeskočí.
JSON soubory se zkopírují do `src/main/resources/dataset/` (build je vezme
do jaru), takže `docker compose up` funguje bez mountování hostitelských
cest — což je akceptační kritérium.

**Proč Java a ne vygenerovat SQL seed Pythonem:** alternativa je reálná a
levnější (Python vyrobí `V2__seed.sql`, Flyway ho aplikuje, žádný Java
kód). Ale importer je v inzerátech běžná úloha — Jackson, dávkové zápisy,
transakce, idempotence — a je to slušný kus Javy k obhájení.
**Rozhodnuto: Java importer** (sekce 9, řádek C).

**Proč `ApplicationRunner` a ne `@PostConstruct`:** `@PostConstruct` běží
při inicializaci beanu, kdy ještě nemusí být hotový celý kontext (a Flyway
migrace už proběhly, ale transakční infrastruktura nemusí být připravená
tak, jak čekáš). `ApplicationRunner` běží až po plném nastartování
kontextu, dostane argumenty a výjimka z něj korektně shodí aplikaci.

**Proč dávky a ne `save()` v cyklu:** 429 + 752 + ~5000 řádků parametrů
jednotlivými inserty je několik tisíc round-tripů. `JdbcTemplate.batchUpdate`
s dávkou 500 to udělá v jednotkách dotazů. **[Python → Java]** V Pythonu bys
sáhl po `executemany`; tohle je jeho ekvivalent.

---

## 7. Testy

### 7.1 Klíčové rozhodnutí: kalkulátor nesmí znát Spring ani JPA

`RaiseSkeletonCalculator` dostane **prostý vstupní record** (úrovně,
parametry skillu, staty monstra) a vrátí výsledek. Žádné repozitáře, žádné
anotace, žádný kontext.

**Proč:** jen tak jde testovat přímo z baseline JSON — bez databáze, bez
Springu, v milisekundách. Servisní vrstva nad ním načte data z DB a předá
je dovnitř. Je to zjednodušená hexagonální architektura: doména je čistá
funkce, infrastruktura je kolem ní. Zároveň je to nejlepší odpověď na
otázku „jak byste to testoval".

### 7.2 Sada testů

| test | typ | co ověřuje |
|---|---|---|
| `Seg5Test` | čistý JUnit | kontrolní tabulka z 1.3 (16→8, 20→16, 29→42, 40→86, 50→126) + chování nad lvl 28 |
| `RaiseSkeletonCalculatorTest` | `@ParameterizedTest` + `@MethodSource` nad `baseline_raise_skeleton.json` | **8 baseline případů**, všech 6 finálních hodnot + `rs_internal_flat` a `total_skill_bonus` |
| `SkillControllerTest` | `@WebMvcTest` + `@MockitoBean` | tvar JSON, stránkování, 404 → ProblemDetail |
| `NormalizeKeyTest` | čistý JUnit | shoda s Python `normalize_key()` na sadě vstupů |
| `SkillRepositoryIT` | **volitelně** `@SpringBootTest` + Testcontainers | Flyway proběhne, filtr + fulltext dotaz vrací co má |

### 7.3 Baseline JSON: kopírovat, nebo číst z `../scripts/tests`?

Test nesmí záviset na cestě mimo Maven modul (rozbije se v Dockeru a v CI).
Návrh: `maven-resources-plugin` ho **při buildu zkopíruje** z
`../scripts/tests/baseline_raise_skeleton.json` do `target/test-classes/`.
Tím zůstane jediný zdroj pravdy v Pythonu a nehrozí, že se kopie rozejdou.
(Pokud půjdeme cestou samostatného repozitáře, tohle padá a kopie se musí
verzovat — další argument pro monorepo.)

### 7.4 Testcontainers ano, nebo ne?

Audit v osekané variantě říká ne. Argument pro: je to častá položka
v inzerátech a integrační test proti reálnému Postgresu má jinou váhu než
H2. Argument proti: `mvn test` pak vyžaduje běžící Docker — kdo si repo
naklonuje bez Dockeru, dostane červený build.

Návrh kompromisu: integrační testy pod Maven profilem `-Pit` (nebo
`@Tag("it")` a Failsafe místo Surefire), takže `mvn test` je zelený vždycky
a `mvn verify -Pit` pustí i ty s Dockerem. Řeší to obojí a je to samo o sobě
věc, o které se dá u pohovoru mluvit.

---

## 8. Pořadí prací

Každý krok končí stavem, který si u sebe spustíš a ověříš. Odhad podle
auditu 5.4 (osekaná varianta 25–35 h celkem).

| # | krok | výstup |
|---|---|---|
| 0 | **Instalace JDK 21 + Docker Desktop** (viz 8.1) | `java -version` hlásí 21, `docker run hello-world` projde |
| 1 | **HOTOVO** — kostra: `pom.xml`, `Application`, `application.yml`, compose s Postgresem, `V1` = `game` + `dataset_version` | `spring-boot:run` nastartuje, `flyway_schema_history` existuje |
| 2 | **HOTOVO** — `V2__skills_and_monsters.sql` + entity `Skill`, `SkillParam`, `Monster`, `MonsterStat` | `ddl-auto=validate` projde |
| 3 | **HOTOVO** — Importer (`ingest`) | `select count(*) from skill` → 429, `monster` → 752 |
| 4 | Repozitáře + dotaz na filtr a fulltext | dotaz vrací správné řádky |
| 5 | DTO, mappery, controllery, `@RestControllerAdvice` | tři GET endpointy vrací JSON |
| 6 | `Seg5` + `RaiseSkeletonCalculator` + parametrizovaný test | **8/8 zelených** |
| 7 | springdoc, README, Dockerfile, plné compose | `docker compose up` → Swagger UI na `/swagger-ui` |
| 8 | *volitelně* Testcontainers IT, GitHub Actions, nasazení (Fly.io / Render) | odkaz do CV |

**Kód píšu já, ty ho čteš, spouštíš a ptáš se.** Ke každému souboru dostaneš
krátké shrnutí — co dělá a proč je to takhle — se zaměřením na to, co je
specifické pro Javu a Spring (DTO vs. entita, `@Transactional`, odvozené
dotazy vs. `@Query`, Flyway vs. `ddl-auto`, dependency injection), ne na
základy syntaxe. Komentáře v kódu drží stejnou linii: vysvětlují **proč**,
ne co dělá řádek.

Po každém kroku spustíš build u sebe. Já kompilátor k dispozici nemám
(1.1), takže tvoje `mvnw test` je jediná zpětná vazba, kterou máme —
proto ty malé přírůstky.

### 8.1 Krok 0 — co nainstalovat

**JDK 21.** Doporučuju Eclipse Temurin 21 (LTS), instalátor `.msi`:
<https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64>
Při instalaci zaškrtnout *Set JAVA_HOME variable* a *Add to PATH*.
Ověření v novém PowerShellu:

```powershell
java -version      # openjdk version "21.x.x"
javac -version     # javac 21.x.x   <- musí být, JRE nestačí
echo $env:JAVA_HOME
```

`javac` je důležitý: „Java" stažená z java.com je JRE a nic nezkompiluje.

**Maven neinstaluj.** IntelliJ IDEA Community má Maven vestavěný, takže
projekt sestavíš i bez něj. Do repa pak přijde Maven Wrapper (`mvnw.cmd`),
který si správnou verzi stáhne sám — každý, kdo repo naklonuje, buildí
přesně tou verzí co ty a nemusí nic instalovat.

Wrapper vygeneruješ jedním příkazem z IntelliJ (panel *Maven* → *Execute
Maven Goal* → `wrapper:wrapper -Dmaven=3.9.11`). Já ho vygenerovat nemůžu —
z Coworku je Maven Central blokovaný (1.1).

**Docker Desktop.** <https://www.docker.com/products/docker-desktop/>
Potřebuje zapnutý WSL2; instalátor to obvykle dořeší sám (restart Windows).
Ověření:

```powershell
docker --version
docker run --rm hello-world
```

Bez Dockeru se dá krok 1–6 odladit i proti Postgresu nainstalovanému
nativně, ale akceptační kritérium `docker compose up` padá — takže ho
chceme.

Kroky 1–7 jsou akceptační kritérium. Krok 8 je to, co z toho udělá odkaz
v životopise.

---

## 9. Rozhodnutí (2026-08-29)

| otázka | rozhodnutí | důsledek |
|---|---|---|
| **A. Prostředí** | JDK 21 ani Docker zatím nejsou | krok 0 = instalace, viz 8.1 |
| **B. Repozitář** | **monorepo**, Java do `backend/` | baseline JSON zůstává jediným zdrojem pravdy, Maven si ho kopíruje při buildu (7.3). Do kořenového README přijde odstavec, který náboráře nasměruje do `backend/` |
| **C. Import dat** | **Java importer**, `skill_calc` až později | `V1` = `game`, `dataset_version`, `skill`, `skill_param`, `monster`, `monster_stat`. `skill_calc` / `skill_calc_ref` / `skill_stat_ref` přijdou přes `V2__`, až bude konzument |
| **D. Dělba práce** | kód píšu já, ty čteš a spouštíš | ke každému souboru shrnutí (co dělá, proč takhle) + komentáře vysvětlující **proč**. Bez základů syntaxe — důraz na Java/Spring specifika a architekturu |
| **E. Testcontainers** | odloženo na krok 8 | `mvn test` musí být zelený i bez běžícího Dockeru. Až přijdou, budou pod profilem `-Pit` (7.4) |

## 10. Co tenhle plán vědomě vynechává

- **Golemy.** `KNOWN_ACCURACY_GAPS.md` bod 1: `dm` křivka kalibrovaná na
  lvl 1, Clay Golem bez golden testu, neověřený režim úrovně u `ln`/`dm`.
  Do slice nepatří ani jako sloupec navíc.
- **Port `expr.py`.** Ověřeno v 1.3, že není potřeba.
- **`skill_calc_ref` a `level_mode`.** Návrh v `SKILLS_PROJECTION.md` 3.2
  platí a neruší se — jen ve slice není konzument. Až přijde, `level_mode`
  bude na každém odkazu, ne příznak na skillu.
- **Účty, build planner, další summony.** Až slice poběží nasazený.
- **Python.** Zůstává offline ingestion nástrojem, generuje
  `data/**/generated/*.json`. Java je runtime, ne náhrada.
