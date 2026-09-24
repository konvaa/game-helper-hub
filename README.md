# Game Helper

[![CI](https://github.com/konvaa/game-helper-hub/actions/workflows/ci.yml/badge.svg)](https://github.com/konvaa/game-helper-hub/actions/workflows/ci.yml)

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
z Python reference implementace, viz níže. Aktuálně umí výpočet statů
summonu **Raise Skeleton** (`POST /api/summons/raise-skeleton/compute`)
a čtení skillů a monster z datasetu.

Technologie: Java 21, Spring Boot 3.5, Maven (přes Maven Wrapper,
instalovat ho nemusíš), PostgreSQL 17 + Flyway, springdoc-openapi.

### Jak to spustit lokálně

Nejrychlejší cesta — potřebuješ jen [Docker
Desktop](https://www.docker.com/products/docker-desktop/):

```bash
cd backend
docker compose up --build
```

API poběží na <http://localhost:8080>. Bez herních dat nastartuje taky
(endpointy vrací prázdné výsledky / `404`); jak si dataset vygenerovat
z vlastní instalace hry a připojit, popisuje
[`backend/README.md`](backend/README.md) (krok 2).

Pro vývoj bez rebuildování image (vyžaduje JDK 21):

```bash
cd backend
docker compose up -d db     # jen PostgreSQL
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

Testy (nepotřebují databázi ani dataset — totéž spouští CI):

```bash
cd backend
./mvnw test
```

> Na Linuxu/macOS může `./mvnw` hlásit `Permission denied` — soubor je
> v gitu uložený bez spustitelného bitu. Pomůže `chmod +x mvnw` (stejně
> to dělá `Dockerfile` i CI workflow).

### Swagger UI

Interaktivní dokumentace všech endpointů s tlačítkem „Try it out“:
<http://localhost:8080/swagger-ui/index.html> (strojově čitelný OpenAPI
popis je na <http://localhost:8080/v3/api-docs>).

### CI

[GitHub Actions](.github/workflows/ci.yml) při každém push a pull requestu
přeloží backend a spustí testy (`./mvnw verify`, Java 21). Stav ukazuje
odznak nahoře.

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
