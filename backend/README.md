# game-helper-api

Spring Boot REST API nad daty Diablo II: Resurrected. Vypočítává staty
přivolaných jednotek (summonů) z herních dat a úrovní skillů.

Plán a zdůvodnění rozhodnutí: [`../docs/PLAN_P1_SPRING_SLICE.md`](../docs/PLAN_P1_SPRING_SLICE.md)
Průběžné poznámky ke kódu: [`../docs/BACKEND_ZAPISNIK.md`](../docs/BACKEND_ZAPISNIK.md)

**Stav: krok 2 z 8** — kostra aplikace, schéma `skill`/`skill_param`/
`monster`/`monster_stat` + JPA entity. Tabulky jsou zatím prázdné (import
přijde v kroku 3), endpointy zatím žádné.

## Rychlý start

```bash
docker compose up -d db          # PostgreSQL na localhost:5432
./mvnw spring-boot:run           # Windows: mvnw.cmd spring-boot:run
```

Aplikace naběhne na <http://localhost:8080>. Endpointy přibudou v kroku 5,
Swagger UI v kroku 7.

Ověření, že migrace proběhly:

```bash
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "\dt"
docker exec -it gamehelper-db psql -U gamehelper -d gamehelper -c "select * from flyway_schema_history;"
```
