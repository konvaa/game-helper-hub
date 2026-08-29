-- V1: identita hry a evidence importovaného datasetu.
--
-- POZOR NA PRAVIDLO FLYWAY: jakmile migrace jednou proběhne, ULOŽÍ SE JEJÍ
-- KONTROLNÍ SOUČET do tabulky flyway_schema_history. Když ten soubor potom
-- změníš, Flyway při dalším startu skončí chybou "Migration checksum mismatch".
-- Změna schématu = NOVÝ soubor V2__..., nikdy editace už aplikovaného.
--
-- Ve fázi vývoje, kdy ještě nic není nasazené, je legitimní zkratka smazat
-- databázi a nechat migrace proběhnout znovu od nuly:
--     docker compose down -v      (-v zahodí i volume s daty)
--     docker compose up -d db

-- ---------------------------------------------------------------------------
-- game
-- ---------------------------------------------------------------------------
-- Proč tahle tabulka existuje, když je zatím jedna hra:
-- skill.game_id chceme mít od prvního dne. Přidat rozlišovací sloupec později
-- znamená přepsat každé unikátní omezení (UNIQUE (skill_key) -> UNIQUE
-- (game_id, skill_key)), každý index a každý dotaz nad skilly. Teď je to jedna
-- tabulka a jeden cizí klíč. Dlouhodobý záměr projektu je víc her, takže to
-- není spekulativní zobecňování.
create table game
(
    -- "generated always as identity" místo staršího serial: je to SQL standard
    -- (PostgreSQL 10+), sloupec vlastní svou sekvenci a nejde do něj omylem
    -- vložit vlastní hodnotu. serial je jen syntaktický cukr nad
    -- "integer default nextval(...)" a tu ochranu nemá.
    id   bigint generated always as identity primary key,

    -- Business klíč. Krátký, stabilní, použitelný v URL a v konfiguraci.
    code text not null unique,

    name text not null
);

comment on table game is 'Podporovaná hra. Zatím jediný řádek, ale FK z ostatních tabulek existuje od začátku.';
comment on column game.code is 'Stabilní kód hry používaný v API a konfiguraci (d2r).';

insert into game (code, name)
values ('d2r', 'Diablo II: Resurrected');

-- ---------------------------------------------------------------------------
-- dataset_version
-- ---------------------------------------------------------------------------
-- Provenience dat. Celý projekt stojí a padá s tím, ze KTERÉHO exportu D2R
-- data pocházejí - hodnoty se mezi patchi mění (viz docs/SKILLS_PROJECTION.md
-- 5.6: patch 3.3 změnil u tří skillů petmax z .lvl na .blvl). Bez téhle
-- evidence nejde říct, jestli je rozdíl proti hře chyba výpočtu, nebo starý
-- dataset.
--
-- Zároveň je to zámek pro idempotentní import (krok 3): když už řádek se
-- stejným content_hash existuje, importer se přeskočí.
create table dataset_version
(
    id             bigint generated always as identity primary key,

    game_id        bigint      not null references game (id),

    -- Verze hry, ze které export pochází (např. "D2R 3.3").
    source_version text        not null,

    -- Cesta k CascView exportu z index.json. Čistě dokumentační - na stroji,
    -- kde API běží, neexistuje.
    excel_dir      text,

    -- SHA-256 obsahu importovaných JSON souborů. Tohle je to, co skutečně
    -- rozhoduje o identitě datasetu; source_version je jen lidský popis.
    content_hash   text        not null,

    imported_at    timestamptz not null default now(),

    -- Proč timestamptz a ne timestamp: timestamp bez časové zóny ukládá
    -- "nástěnný čas" bez informace, v jaké zóně vznikl. Při běhu v Dockeru
    -- (UTC) a lokálně (Europe/Prague) by se ty hodnoty tiše nesrovnávaly.

    constraint uq_dataset_version_hash unique (game_id, content_hash)
);

comment on table dataset_version is 'Evidence importovaného datasetu - z jakého exportu hry data pocházejí.';
comment on column dataset_version.content_hash is 'SHA-256 importovaných JSON souborů; zámek pro idempotentní import.';
