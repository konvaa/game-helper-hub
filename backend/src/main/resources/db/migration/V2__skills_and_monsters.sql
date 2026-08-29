-- V2: skill + monster a jejich podřízené tabulky.
--
-- Projekce vychází z docs/SKILLS_PROJECTION.md (322 -> 26 sloupců pro
-- skill). Je to MINIMUM pro čtyři endpointy slice, ne celé navržené schéma -
-- skill_calc, skill_calc_ref a skill_stat_ref z projekce (sekce 3.2) tu
-- záměrně NEJSOU, protože ve slice pro ně není konzument. Přibudou přes
-- V3+, až GET /api/skills/{key} bude opravdu vracet výrazy. Rozhodnutí je
-- zapsané v docs/PLAN_P1_SPRING_SLICE.md, sekce 9, řádek C.

-- ---------------------------------------------------------------------------
-- monster (vytváří se před skill, protože na ni skill.monster_key odkazuje)
-- ---------------------------------------------------------------------------
create table monster
(
    id          bigint generated always as identity primary key,
    game_id     bigint not null references game (id),

    -- Klíč z monsters_base.json (např. "necroskeleton"). Musí to být výstup
    -- STEJNÉ normalize_key() funkce, kterou používá Python engine
    -- (scripts/summon_engine/loader.py) - jinak by se join přes skill.summon
    -- na některé monstry netrefil. Zjištění pro import (krok 3): syrové
    -- klíče nejsou vždy už normalizované - "trap-firebolt", "Expansion" a
    -- 7 dalších z 752 by se normalizací změnily. Importer musí normalizovat
    -- OBOJÍ - klíč monstra při importu monster i hodnotu skill.summon při
    -- importu skillu - ne jen zkopírovat syrový text.
    monster_key text   not null unique,

    name_str    text
);

comment on table monster is 'Identita monstra (z monstats.txt / monsters_base.json). Staty per obtížnost jsou v monster_stat.';
comment on column monster.monster_key is 'Normalizovaný klíč (normalize_key), ne nutně identický se syrovým klíčem z JSON.';

-- ---------------------------------------------------------------------------
-- monster_stat — jeden řádek na (monstrum, obtížnost)
-- ---------------------------------------------------------------------------
-- Rozhodnutí "TEXT + CHECK, ne PG ENUM" je z docs/PLAN_P1_SPRING_SLICE.md 4.4:
-- ALTER TYPE ... ADD VALUE je v Postgresu nepříjemný (ve starších verzích
-- nejde spustit uvnitř transakce a hodnotu z enumu nejde odebrat). CHECK dá
-- stejnou záruku a mění se obyčejnou migrací.
create table monster_stat
(
    id         bigint generated always as identity primary key,
    monster_id bigint  not null references monster (id),

    difficulty text    not null check (difficulty in ('normal', 'nightmare', 'hell')),

    -- Úroveň monstra na dané obtížnosti. NULL je legitimní hodnota - viz
    -- necroskeleton, kde ho hra vůbec nedefinuje (summon nemá vlastní úroveň
    -- pro účely monster-levelu, staty se počítají jinak).
    level      smallint,

    min_hp     integer,
    max_hp     integer,
    ac         integer,

    -- Attack 1 (fyzický útok, ten čte RaiseSkeletonModel).
    a1_min_d   integer,
    a1_max_d   integer,
    a1_th      integer,

    -- Attack 2 - přidáno do datasetu kvůli Fire Golemovi a podobným
    -- monstrům s druhým útokem (viz index.json.notes). Slice ho nečte,
    -- ale je to stejná řada sloupců jako Attack 1 a levné ho mít hned.
    a2_min_d   integer,
    a2_max_d   integer,
    a2_th      integer,

    -- Skill 1 (magický/kouzelný útok). Ve slice nepoužito, ale monstats.txt
    -- ho nese a je to stejný vzor jako a1_/a2_.
    s1_min_d   integer,
    s1_max_d   integer,
    s1_th      integer,

    constraint uq_monster_stat_diff unique (monster_id, difficulty)
);

comment on table monster_stat is 'Staty jednoho monstra pro jednu obtížnost (normal/nightmare/hell).';

-- ---------------------------------------------------------------------------
-- skill
-- ---------------------------------------------------------------------------
create table skill
(
    id                  bigint generated always as identity primary key,
    game_id             bigint  not null references game (id),

    -- Business klíč - normalizovaný, stabilní, používá se v URL
    -- (GET /api/skills/{key}). Musí být bit-shodný s Python normalize_key(),
    -- jinak se rozejdou baseline testy, které volají oba enginy na stejný
    -- vstup a čekají stejný výstup.
    skill_key           text     not null unique,

    -- *Id z exportu. NENÍ autoritativní klíč - je to formálně komentářový
    -- sloupec [*], který hra nečte, a engine přiřazuje ID pořadím řádků
    -- (SKILLS_PROJECTION.md 3.1.A). Uložen jen pro dohledatelnost k
    -- původnímu řádku exportu.
    ext_id              integer,

    -- Surový název ("Raise Skeleton") - to, co se zobrazí v UI a hledá
    -- fulltextem (viz index níž).
    name                text     not null,

    charclass           text,

    -- Odkaz do skilldesc_raw (lokalizační texty). Tabulka skilldesc do
    -- slice nepatří - žádný ze čtyř endpointů popisné texty nevrací -
    -- sloupec je NULL-able text, ne FK, aby import neselhal na skillech
    -- bez popisu (146 z 429 podle SKILLS_PROJECTION.md 3.1.A).
    skilldesc_key       text,

    reqlevel            smallint,

    -- POZOR: strop INVESTOVANÝCH BODŮ, ne efektivní úrovně (u všech 429
    -- skillů v datasetu shodou okolností = 20, ale schéma to nesmí
    -- předpokládat). Compute endpoint tímhle sloupcem NIKDY neomezuje vstup
    -- - viz SKILLS_PROJECTION.md 2.5 a PLAN 4.4.
    maxlvl              smallint,

    -- --- segmentové škálování (seg5) - jádro celé projekce, viz
    -- SKILLS_PROJECTION.md sekce 2. EMinLev5/EMaxLev5 jsou jediné sloupce,
    -- které určují chování nad efektivní úrovní 28, a nemají horní mez.
    etype               text,
    emin                integer,
    emax                integer,
    emin_lev1           integer,
    emin_lev2           integer,
    emin_lev3           integer,
    emin_lev4           integer,
    emin_lev5           integer,
    emax_lev1           integer,
    emax_lev2           integer,
    emax_lev3           integer,
    emax_lev4           integer,
    emax_lev5           integer,

    -- --- summon ---
    -- NULL u skillů, které nic nepřivolávají (drtivá většina z 429).
    -- FK na monster(monster_key) - Postgres FK na NULL hodnotu nekontroluje
    -- vůbec nic, takže "skill bez summonu" projde bez výjimky.
    monster_key         text references monster (monster_key),
    pettype             text,

    -- VÝRAZ, ne číslo - "(lvl < 4) ?lvl:(2+lvl/3)" u Raise Skeleton.
    -- Kdyby byl sloupec numerický, import by na první netriviální hodnotě
    -- spadl. Ve slice se hodnota nepoužívá skrz vyhodnocovač (počet
    -- skeletonů má RaiseSkeletonCalculator zakódovaný natvrdo, viz
    -- PLAN_P1_SPRING_SLICE.md 1.3) - sloupec je tu pro úplnost a pro detail
    -- endpoint, který ho může vrátit jako surový text.
    petmax_expr         text,

    hit_shift           smallint,

    -- --- výrazové sloupce (TEXT ze stejného důvodu jako petmax_expr) ---
    calc1_expr          text,
    aurastat1           text,
    aurastat1_calc_expr text,

    src_dam             smallint
);

comment on table skill is 'Projekce skills_raw (322 -> 26 sloupců). Viz docs/SKILLS_PROJECTION.md sekce 3.';
comment on column skill.ext_id is 'Neautoritativní surrogate z exportu (*Id) - viz SKILLS_PROJECTION.md 3.1.A. Autoritativní klíč je skill_key.';
comment on column skill.maxlvl is 'Strop investovaných bodů. NIKDY nepoužívat jako strop efektivní úrovně ve výpočtu.';
comment on column skill.petmax_expr is 'Surový D2 výraz (např. "(lvl < 4) ?lvl:(2+lvl/3)"), ne vypočtená hodnota.';

-- Filtr podle třídy (GET /api/skills?charClass=nec). Částečný index -
-- 189 ze 429 skillů má charclass prázdný (obecné/monster-only skilly, viz
-- SKILLS_PROJECTION.md 3.1.A) a filtr se na NULL nikdy neptá, takže není
-- důvod je v indexu nosit.
create index idx_skill_charclass on skill (charclass) where charclass is not null;

-- Postgres NEindexuje cizí klíče automaticky - to dělá jen pro PRIMARY KEY
-- a UNIQUE. Bez tohohle indexu by každé hledání "které skilly přivolávají
-- tohle monstrum" (a i kontrola FK při případném mazání monstra) dělalo
-- sekvenční průchod přes celou tabulku skill.
create index idx_skill_monster_key on skill (monster_key);

-- Fulltext v názvu (GET /api/skills?q=skel), viz PLAN_P1_SPRING_SLICE.md 4.5.
-- pg_trgm rozkládá text na trojice znaků ("ske","kel","ele",...) - proto umí
-- najít shodu KDEKOLI v řetězci (infix), na rozdíl od vestavěného
-- plnotextového vyhledávání Postgresu, které pracuje se slovy a jejich
-- kořeny a neumí najít "skel" uvnitř "Skeleton".
-- Rozšíření je od PG13 "trusted" - nepotřebuje superuser práva, stačí
-- CREATE právo na databázi (to gamehelper uživatel z docker-compose má,
-- protože je vlastníkem databáze).
create extension if not exists pg_trgm;

create index idx_skill_name_trgm on skill using gin (lower(name) gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- skill_param — Param1..Param12 jako řádky místo 12 sloupců navíc
-- ---------------------------------------------------------------------------
create table skill_param
(
    id          bigint generated always as identity primary key,
    skill_id    bigint   not null references skill (id),

    -- Pořadí 1..12, odpovídá ParamN ve zdroji.
    idx         smallint not null check (idx between 1 and 12),

    -- V celém datasetu (429 skillů x 12 parametrů) jsou hodnoty vždy celá
    -- čísla - ověřeno skenem skills_raw.json. Python (make_vars) je přesto
    -- interně drží jako float, protože jimi prochází aritmetika D2 výrazů.
    -- INTEGER tady je vědomé rozhodnutí, ne přehlédnutí: pokud budoucí
    -- refresh datasetu přinese desetinnou hodnotu, chceme, aby import
    -- spadl nahlas na chybě typu, ne aby ji tiše zaokrouhlil.
    value       integer,

    -- Obsah "*ParamN Description" (např. "HP % per level"). Není to herní
    -- data, ale je to jediná dokumentace k tomu, co který ParamN znamená -
    -- SKILLS_PROJECTION.md 4.1 ji označuje za cennější než většinu
    -- "skutečných" sloupců.
    description text,

    constraint uq_skill_param unique (skill_id, idx)
);

comment on table skill_param is 'Param1..Param12 ze skills_raw, rozložené na řádky. Nahrazuje 12 sloupců na skill.';
