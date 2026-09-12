# game-helper-api

Spring Boot REST API nad daty Diablo II: Resurrected. Počítá staty
přivolaných jednotek (summonů) z herních dat a úrovní skillů — mimo jiné
odpovídá na otázku "kolik HP a damage bude mít můj vyvolaný kostlivec na
úrovni skillu 20?", bez nutnosti to ručně dopočítávat z herních tabulek.

Plán a zdůvodnění architektonických rozhodnutí:
[`../docs/PLAN_P1_SPRING_SLICE.md`](../docs/PLAN_P1_SPRING_SLICE.md)

**Stav: krok 7 z 8** — kompletní P1 slice (jedna hra, jeden skill: Raise
Skeleton), včetně dokumentovaného API a spuštění v Dockeru.

> **Herní data nejsou součástí repozitáře.** Vyexportovaná data z D2R jsou
> majetkem Blizzardu a nemám právo je šířit. Aplikace proto běží ve dvou
> režimech: **bez datasetu** nastartuje a endpointy vrací prázdné výsledky,
> **s datasetem** (který si vygeneruješ ze své vlastní instalace hry) počítá
> naostro. Postup je níž ve dvou krocích.

## Co to umí (čtyři endpointy)

- `GET /api/skills?charClass=&q=&page=&size=&sort=` — stránkovaný seznam
  skillů, s volitelným filtrem podle povolání a fulltextem v názvu
- `GET /api/skills/{key}` — detail jednoho skillu
- `GET /api/monsters/{id}` — detail monstra se staty pro všechny tři
  obtížnosti (normal/nightmare/hell) najednou
- `POST /api/summons/raise-skeleton/compute` — výpočet statů přivolaného
  kostlivce (Raise Skeleton). **8/8 shodných výsledků s referenční Python
  implementací** (`RaiseSkeletonCalculatorTest`, ověřeno proti
  `scripts/tests/baseline_raise_skeleton.json`). Neplatný vstup vrací `400`
  s `ProblemDetail`, který přesně říká, které pole a proč selhalo (viz
  `ApiExceptionHandler`) — ne obecnou hlášku.

Validace vstupu i tvar chybové odpovědi fungují i bez datasetu — na to
data nepotřebují.

## Krok 1: spuštění aplikace

Jediná podmínka: nainstalovaný a spuštěný [Docker
Desktop](https://www.docker.com/products/docker-desktop/) (JDK ani Maven
není potřeba — obojí je uvnitř image).

```bash
docker compose up --build
```

Poběží dva kontejnery: `gamehelper-db` (PostgreSQL, se schématem
vytvořeným Flyway migracemi při startu API) a `gamehelper-api` (tahle
appka, na <http://localhost:8080>). API čeká na zdravou databázi
(`depends_on: condition: service_healthy` v `docker-compose.yml`), takže
pořadí startu řešit nemusíš.

Bez datasetu se v logu objeví `WARN` s tím, co chybí a kde se to hledalo:

```
Dataset nenalezen - chybi 3 z 3 souboru: [skills_raw.json, monsters_base.json, index.json].
Hledano v: classpath:dataset/ (vychozi - property 'game-helper.dataset.path' neni nastavena)
Aplikace nastartuje s PRAZDNOU databazi; endpointy budou vracet prazdne vysledky.
```

Aplikace poběží dál. `GET /api/skills` vrátí prázdnou stránku
(`"content": [], "totalElements": 0`), Swagger UI a validace fungují
normálně; dotaz na konkrétní skill nebo výpočet summonu vrátí `404`
s vysvětlením, že daný záznam v datové sadě není.

Zastavení: <kbd>Ctrl+C</kbd>, pak `docker compose down` (data v databázi
zůstanou zachovaná v pojmenovaném volume; `docker compose down -v` je
smaže a nechá migrace proběhnout od nuly při dalším startu).

## Krok 2: dodání datasetu

### 2a. Vygenerování dat z vlastní instalace hry

Dataset se staví z herních `.txt` tabulek vyexportovaných z tvé instalace
D2R (CascView → `data/global/excel`). Skript v kořeni repozitáře:

```bash
python scripts/rebuild_dataset.py
```

Vznikne adresář s JSON soubory; API z nich potřebuje tři —
`skills_raw.json`, `monsters_base.json` a `index.json`. Jak vypadají,
je vidět na `*.example.json` vedle nich (12 záznamů, reálné schéma).

### 2b. Nasměrování aplikace na dataset

Cesta k adresáři je konfigurovatelná — property
**`game-helper.dataset.path`**, resp. proměnná prostředí
**`GAME_HELPER_DATASET_PATH`**. Když je prázdná (výchozí stav), hledá se
`dataset/` na classpath, tj. uvnitř jaru.

**Při lokálním běhu** stačí proměnná prostředí, nic se nikam nekopíruje:

```bash
# Linux / macOS
GAME_HELPER_DATASET_PATH=/cesta/k/vygenerovanemu/datasetu ./mvnw spring-boot:run

# Windows (PowerShell)
$env:GAME_HELPER_DATASET_PATH = "D:\cesta\k\datasetu"; .\mvnw.cmd spring-boot:run
```

Nebo natvrdo v `src/main/resources/application.yml`
(`game-helper.dataset.path`) — vhodné jen pro lokální hraní, protože je to
cesta specifická pro tvůj stroj.

**V Dockeru** je nejjednodušší cesta zabalit data do image: zkopíruj ty tři
JSON soubory do `src/main/resources/dataset/` a spusť `docker compose up
--build`. Nezůstane po nich stopa v gitu — `.gitignore` v kořeni
repozitáře pouští dovnitř jen `*.example.json`.

Když je nechceš kopírovat, připoj je do kontejneru přes
`docker-compose.override.yml` (Compose ho načte automaticky vedle
`docker-compose.yml`; je v `.gitignore`, takže tvoje cesta nikomu
nepřistane v repozitáři):

```yaml
services:
  api:
    environment:
      GAME_HELPER_DATASET_PATH: /dataset
    volumes:
      - /cesta/k/vygenerovanemu/datasetu:/dataset:ro
```

### Ověření, že se data naimportovala

Při startu s daty jde do logu místo `WARN` tenhle `INFO` řádek:

```
Import hotovy: 752 monster, ... radku, 429 skillu, ... skill_param radku (hash a1b2c3d4e5f6...).
```

Import je idempotentní — při dalším startu se stejnými daty se přeskočí
(`Dataset (hash ...) uz je naimportovany, preskakuji.`). Kdybys dataset
vyměnil za jinou verzi, staré řádky by kolidovaly na unikátních klíčích;
řešení je `docker compose down -v` a start od nuly.

Chybějící dataset aplikaci neshodí, ale **poškozený ano** — nevalidní JSON
nebo neznámá hodnota v číselném poli vyhodí `DatasetImportException` a
aplikace nenastartuje. To je záměr: "žádná data" je konfigurace, "rozbitá
data" je chyba.

## Co si vyzkoušet (s naimportovaným datasetem)

**Swagger UI** — interaktivní dokumentace všech čtyř endpointů, rovnou
s tlačítkem "Try it out": <http://localhost:8080/swagger-ui/index.html>

Nebo z příkazové řádky:

```bash
curl http://localhost:8080/api/skills/raise_skeleton

curl -X POST http://localhost:8080/api/summons/raise-skeleton/compute \
  -H "Content-Type: application/json" \
  -d '{"raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "hell"}'
```

Očekávaný výstup druhého příkazu: `hp: 487, physMin: 85, physMax: 87,
defense: 471, attackRating: 471, count: 8` (přesně baseline případ
`rs20_sm11`).

A jak vypadá chyba při neplatném vstupu (tohle funguje i bez datasetu —
validace běží dřív, než se sáhne do databáze):

```bash
curl -X POST http://localhost:8080/api/summons/raise-skeleton/compute \
  -H "Content-Type: application/json" \
  -d '{"raiseSkeletonLevel": 0, "skeletonMasteryLevel": 11, "difficulty": "nesmysl"}'
```

Vrátí `400` a v poli `errors` dvě položky — jednu pro `raiseSkeletonLevel`
(musí být alespoň 1) a jednu pro `difficulty` (s `allowedValues:
["normal", "nightmare", "hell"]`).

## Vývoj (bez rebuildování image při každé změně kódu)

Rychlejší cyklus pro úpravu kódu: databáze v Dockeru, appka spuštěná přímo
(IntelliJ, nebo Maven Wrapper — Maven instalovat nemusíš):

```bash
docker compose up -d db          # jen PostgreSQL, na localhost:5432
./mvnw spring-boot:run           # Windows: mvnw.cmd spring-boot:run
```

Testy (bez potřeby běžící databáze a **bez potřeby datasetu** — čistě
doménová logika; baseline pro porovnání s Python referencí je součástí
repozitáře v `scripts/tests/`):

```bash
./mvnw test
```

Testy včetně repozitářové integrace — jako jediné potřebují běžící databázi
**s naimportovaným datasetem** (kroky 1 a 2 výš), protože ověřují konkrétní
počty řádků nad reálnými daty:

```bash
./mvnw test -Pit
```

Ověření, že migrace proběhly:

```bash
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "\dt"
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "select * from flyway_schema_history;"
```
