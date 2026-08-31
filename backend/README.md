# game-helper-api

Spring Boot REST API nad daty Diablo II: Resurrected. Počítá staty
přivolaných jednotek (summonů) z herních dat a úrovní skillů — mimo jiné
odpovídá na otázku "kolik HP a damage bude mít můj vyvolaný kostlivec na
úrovni skillu 20?", bez nutnosti to ručně dopočítávat z herních tabulek.

Plán a zdůvodnění architektonických rozhodnutí:
[`../docs/PLAN_P1_SPRING_SLICE.md`](../docs/PLAN_P1_SPRING_SLICE.md)
Průběžné poznámky ke kódu, krok po kroku:
[`../docs/BACKEND_ZAPISNIK.md`](../docs/BACKEND_ZAPISNIK.md)

**Stav: krok 7 z 8** — kompletní P1 slice (jedna hra, jeden skill: Raise
Skeleton), včetně dokumentovaného API a jednopříkazového spuštění v Dockeru.

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

## Spuštění jedním příkazem

Jediná podmínka: nainstalovaný a spuštěný [Docker
Desktop](https://www.docker.com/products/docker-desktop/) (JDK ani Maven
není potřeba — obojí je uvnitř image).

```bash
docker compose up --build
```

Poběží dva kontejnery: `gamehelper-db` (PostgreSQL, s daty naimportovanými
při prvním startu API přes Flyway migrace) a `gamehelper-api` (tahle
appka, na <http://localhost:8080>). API čeká na zdravou databázi
(`depends_on: condition: service_healthy` v `docker-compose.yml`), takže
pořadí startu řešit nemusíš.

Zastavení: <kbd>Ctrl+C</kbd>, pak `docker compose down` (data v databázi
zůstanou zachovaná ve pojmenovaném volume; `docker compose down -v` je
smaže a nechá migrace proběhnout od nuly při dalším startu).

## Co si vyzkoušet

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

A jak vypadá chyba při neplatném vstupu:

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

Testy (bez potřeby běžící databáze — čistě doménová logika):

```bash
./mvnw test
```

Testy včetně repozitářové integrace (potřebuje `docker compose up -d db`
předem):

```bash
./mvnw test -Pit
```

Ověření, že migrace proběhly:

```bash
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "\dt"
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "select * from flyway_schema_history;"
```
