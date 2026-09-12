package cz.libor.gamehelper.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.libor.gamehelper.common.NormalizeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Naimportuje trojici {@code skills_raw.json}, {@code monsters_base.json}
 * a {@code index.json} do Postgresu. Volá se z {@link DatasetImporter} při
 * startu aplikace.
 *
 * <p><b>Odkud se soubory čtou.</b> Výchozí umístění je classpath -
 * adresář {@code dataset/} zabalený v jaru. Property
 * {@code game-helper.dataset.path} (proměnná prostředí
 * {@code GAME_HELPER_DATASET_PATH}) ho přepíše na adresář na disku, takže
 * si každý může ukázat na vlastní vygenerovaný dataset bez rebuildu jaru.
 * Rozhodování je celé v {@link #datasetResource(String)}.
 *
 * <p><b>Dataset je VOLITELNÝ - když chybí, aplikace přesto naběhne.</b>
 * Vyexportovaná herní data D2R nejsou součástí repozitáře (nemáme práva na
 * jejich šíření), takže "čerstvý klon bez dat" je běžný a legitimní stav,
 * ne porucha: import se přeskočí, do logu jde WARN s tím, co přesně chybí,
 * kde se to hledalo a jak si dataset vygenerovat, a aplikace běží nad
 * prázdnou databází - endpointy vrací prázdné výsledky.
 *
 * <p>Fatální zůstává dataset, který TU JE, ale je rozbitý: nevalidní JSON,
 * neznámá obtížnost, nečíselná hodnota v číselném poli. To rozlišení je
 * záměrné - "žádná data" je konfigurace, "poškozená data" je chyba, a chyba
 * se má ozvat nahlas při startu (viz {@link DatasetImporter}), ne se tiše
 * přejít a projevit se až divným výsledkem výpočtu o týden později.
 *
 * <p><b>Idempotence - co import řeší a co ne.</b> Řeší "stejná data, restart
 * aplikace" (přeskočí - běžný případ při vývoji, kdy Libor restartuje
 * aplikaci opakovaně) a "prázdná databáze, první spuštění" (naimportuje).
 * NEŘEŠÍ "jiná data nad existujícími řádky": kdyby se dataset refreshnul
 * (nový CascView export → jiný {@code content_hash}), staré řádky
 * {@code skill}/{@code monster} by kolidovaly na {@code UNIQUE(skill_key)}/
 * {@code UNIQUE(monster_key)} a import by spadl na chybě. Řešení v tuhle
 * chvíli je stejné jako při změně schématu -
 * {@code docker compose down -v}. Skutečné verzování dat (víc verzí
 * datasetu vedle sebe v jedné databázi) je mimo rozsah slice; jedna hra,
 * jeden aktuální dataset.
 *
 * <p><b>Proč JdbcTemplate, ne JPA repozitáře, pro zápis.</b> Primární klíče
 * jsou {@code GenerationType.IDENTITY} (viz V1/V2 migrace - "generated
 * always as identity"), a tahle strategie Hibernate DÁVKOVÁNÍ INSERTŮ
 * znemožňuje - po každém řádku musí zpátky pro vygenerované ID, takže by
 * `repository.saveAll(...)` udělal ~1900 samostatných round-tripů. Řešení
 * tady: dávkový insert rodičů (monster/skill) přes
 * {@link JdbcTemplate#batchUpdate}, pak JEDEN levný SELECT zpátky (id +
 * business klíč) do mapy v paměti, pak dávkový insert dětí
 * (monster_stat/skill_param) s ID z týhle mapy. Čtyři dávky + dva SELECTy
 * navíc, žádná per-řádková komunikace. Zvažoval jsem číst generované klíče
 * rovnou z dávky (JDBC to přes {@code Statement.getGeneratedKeys()} po
 * {@code executeBatch()} umí, pgjdbc konkrétně zachovává pořadí) - ale je to
 * driver-specific a Spring JdbcTemplate na to nemá pohodlné API. SELECT
 * zpátky podle business klíče je přenositelnější a stejně levný pro tenhle
 * objem dat (jednorázový import při startu, ne request na hot path).
 */
@Service
public class DatasetImportService {

    private static final Logger log = LoggerFactory.getLogger(DatasetImportService.class);

    private static final Set<String> VALID_DIFFICULTIES = Set.of("normal", "nightmare", "hell");

    private static final String SKILLS_FILE = "skills_raw.json";
    private static final String MONSTERS_FILE = "monsters_base.json";
    private static final String INDEX_FILE = "index.json";

    /**
     * Všechny tři soubory musí být k dispozici, jinak se import přeskočí
     * celý. Nemá smysl naimportovat skilly bez monster (compute endpoint
     * potřebuje obojí) ani dataset bez {@code index.json} (z něj se bere
     * {@code excel_dir} do {@code dataset_version}) - polovičatý import by
     * vypadal jako úspěch a selhal by až za běhu.
     */
    private static final List<String> REQUIRED_FILES = List.of(SKILLS_FILE, MONSTERS_FILE, INDEX_FILE);

    /** Adresář na classpath (uvnitř jaru), když {@code dataset.path} není nastavená. */
    private static final String CLASSPATH_DATASET_DIR = "dataset";

    private static final String INSERT_MONSTER_SQL =
            "INSERT INTO monster (game_id, monster_key, name_str) VALUES (?, ?, ?)";

    private static final String SELECT_MONSTER_IDS_SQL =
            "SELECT id, monster_key FROM monster WHERE game_id = ?";

    private static final String INSERT_MONSTER_STAT_SQL = """
            INSERT INTO monster_stat
                (monster_id, difficulty, level, min_hp, max_hp, ac,
                 a1_min_d, a1_max_d, a1_th, a2_min_d, a2_max_d, a2_th,
                 s1_min_d, s1_max_d, s1_th)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_SKILL_SQL = """
            INSERT INTO skill
                (game_id, skill_key, ext_id, name, charclass, skilldesc_key,
                 reqlevel, maxlvl, etype, emin, emax,
                 emin_lev1, emin_lev2, emin_lev3, emin_lev4, emin_lev5,
                 emax_lev1, emax_lev2, emax_lev3, emax_lev4, emax_lev5,
                 monster_key, pettype, petmax_expr, hit_shift,
                 calc1_expr, aurastat1, aurastat1_calc_expr, src_dam)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SKILL_IDS_SQL =
            "SELECT id, skill_key FROM skill WHERE game_id = ?";

    private static final String INSERT_SKILL_PARAM_SQL =
            "INSERT INTO skill_param (skill_id, idx, value, description) VALUES (?, ?, ?, ?)";

    private static final String SELECT_GAME_ID_SQL =
            "SELECT id FROM game WHERE code = ?";

    private static final String COUNT_DATASET_VERSION_SQL =
            "SELECT count(*) FROM dataset_version WHERE game_id = ? AND content_hash = ?";

    private static final String INSERT_DATASET_VERSION_SQL =
            "INSERT INTO dataset_version (game_id, source_version, excel_dir, content_hash) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String gameCode;
    private final String sourceVersion;
    /** {@code null} = property není nastavená, čte se z classpath. */
    private final Path datasetDir;

    /**
     * @param datasetPath adresář na disku s datasetem, nebo prázdný řetězec
     *                    pro výchozí classpath umístění. Výchozí hodnota
     *                    {@code :} (prázdno) přímo tady je schválně - bean
     *                    se pak dá postavit i v testu bez celého
     *                    {@code application.yml}, a "nenastaveno" znamená
     *                    totéž jako "nastaveno na prázdno".
     * @throws DatasetImportException hodnota property není použitelná jako
     *                                cesta. Tohle spadne ZÁMĚRNĚ, na rozdíl
     *                                od chybějícího datasetu: "adresář, kam
     *                                jsi ukázal, neexistuje" je stav, kterým
     *                                se dá běžet dál, ale "to, cos napsal,
     *                                není cesta" je překlep v konfiguraci -
     *                                a ten se má ozvat hned, ne tichým
     *                                přepnutím na classpath, kde uživatel
     *                                svoje data taky nenajde.
     */
    public DatasetImportService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${game-helper.dataset.game-code}") String gameCode,
            @Value("${game-helper.dataset.source-version}") String sourceVersion,
            @Value("${game-helper.dataset.path:}") String datasetPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.gameCode = gameCode;
        this.sourceVersion = sourceVersion;
        this.datasetDir = toDatasetDir(datasetPath);
    }

    private static Path toDatasetDir(String datasetPath) {
        String trimmed = datasetPath == null ? "" : datasetPath.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Path.of(trimmed);
        } catch (InvalidPathException e) {
            throw new DatasetImportException(
                    "Property 'game-helper.dataset.path' = '" + trimmed + "' neni platna cesta k adresari", e);
        }
    }

    /**
     * Vstupní bod volaný z {@link DatasetImporter}. Musí být zavolaný
     * Z JINÉHO beanu (ne interním {@code this.importIfNeeded()} voláním
     * uvnitř téhle třídy) - {@code @Transactional} funguje přes Spring proxy,
     * která zachytává jen VNĚJŠÍ volání metody. Sebe-volání (self-invocation)
     * proxy obchází úplně a anotace by se tiše neuplatnila - žádná chyba,
     * žádné varování, prostě by celá metoda běžela BEZ transakce. Přesně
     * proto je tenhle import rozdělený na dvě třídy: {@link DatasetImporter}
     * (ApplicationRunner, žádná transakce) volá tuhle metodu na jiném beanu.
     *
     * <p>Kontrola přítomnosti datasetu je ZÁMĚRNĚ úplně první, ještě před
     * {@link #resolveGameId()} - jinak by uživatel s prázdným klonem dostal
     * jako první hlášku dotaz do databáze, ne odpověď na svou skutečnou
     * otázku ("kde mám data?").
     */
    @Transactional
    public void importIfNeeded() {
        List<String> missing = missingDatasetFiles();
        if (!missing.isEmpty()) {
            logMissingDataset(missing);
            return;
        }

        long gameId = resolveGameId();

        byte[] skillsBytes = readDatasetBytes(SKILLS_FILE);
        byte[] monstersBytes = readDatasetBytes(MONSTERS_FILE);
        String contentHash = sha256Hex(skillsBytes, monstersBytes);

        if (alreadyImported(gameId, contentHash)) {
            log.info("Dataset (hash {}...) uz je naimportovany, preskakuji.", contentHash.substring(0, 12));
            return;
        }

        IndexFile index = readJson(INDEX_FILE, IndexFile.class);
        SkillsRawFile skillsFile = parseJson(skillsBytes, SkillsRawFile.class);
        MonstersBaseFile monstersFile = parseJson(monstersBytes, MonstersBaseFile.class);

        Map<String, Long> monsterIdByKey = importMonsters(gameId, monstersFile);
        int monsterStatRows = importMonsterStats(monstersFile, monsterIdByKey);
        Map<String, Long> skillIdByKey = importSkills(gameId, skillsFile);
        int skillParamRows = importSkillParams(skillsFile, skillIdByKey);

        recordDatasetVersion(gameId, index.excelDir(), contentHash);

        log.info(
                "Import hotovy: {} monster, {} monster_stat radku, {} skillu, {} skill_param radku (hash {}...).",
                monsterIdByKey.size(), monsterStatRows, skillIdByKey.size(), skillParamRows,
                contentHash.substring(0, 12));
    }

    // -------------------------------------------------------------------
    // monster + monster_stat
    // -------------------------------------------------------------------

    private Map<String, Long> importMonsters(long gameId, MonstersBaseFile monstersFile) {
        List<Object[]> args = new ArrayList<>(monstersFile.monsters().size());
        for (Map.Entry<String, MonstersBaseFile.MonsterRawRecord> entry : monstersFile.monsters().entrySet()) {
            // Normalizovat se MUSÍ přesně tak, jako se normalizuje
            // skill.monster_key (viz importSkills) - jinak by JOIN mezi
            // nimi na 9 ze 752 klicu tise nic nenasel. Viz NormalizeKey
            // javadoc pro overeni na datech.
            String monsterKey = NormalizeKey.normalize(entry.getKey());
            String nameStr = blankToNull(entry.getValue().nameStr());
            args.add(new Object[]{gameId, monsterKey, nameStr});
        }
        jdbcTemplate.batchUpdate(INSERT_MONSTER_SQL, args);

        Map<String, Long> idByKey = new HashMap<>(args.size() * 2);
        jdbcTemplate.query(
                SELECT_MONSTER_IDS_SQL,
                // Blokove telo (se slozenymi zavorkami), ne
                // "rs -> idByKey.put(...)" - viz vysvetleni u druheho
                // vyskytu stejneho vzoru v importSkills().
                (RowCallbackHandler) rs -> {
                    idByKey.put(rs.getString("monster_key"), rs.getLong("id"));
                },
                gameId);
        return idByKey;
    }

    private int importMonsterStats(MonstersBaseFile monstersFile, Map<String, Long> monsterIdByKey) {
        List<Object[]> args = new ArrayList<>();
        for (Map.Entry<String, MonstersBaseFile.MonsterRawRecord> entry : monstersFile.monsters().entrySet()) {
            String monsterKey = NormalizeKey.normalize(entry.getKey());
            Long monsterId = monsterIdByKey.get(monsterKey);
            if (monsterId == null) {
                // Nemelo by nastat - monsterIdByKey vznikla ze stejneho
                // seznamu monster o radek vys. Kdyby presto ano, je lepsi
                // spadnout nahlas nez tise vynechat staty monstra.
                throw new DatasetImportException("Monstrum '" + entry.getKey() + "' nema pridelene ID po importu");
            }
            Map<String, MonstersBaseFile.DifficultyStatsRaw> base = entry.getValue().base();
            if (base == null) {
                continue;
            }
            for (Map.Entry<String, MonstersBaseFile.DifficultyStatsRaw> diffEntry : base.entrySet()) {
                String difficulty = diffEntry.getKey();
                if (!VALID_DIFFICULTIES.contains(difficulty)) {
                    throw new DatasetImportException(
                            "Neznama obtiznost '" + difficulty + "' u monstra '" + entry.getKey() + "'"
                                    + " - CHECK v monster_stat zna jen normal/nightmare/hell");
                }
                MonstersBaseFile.DifficultyStatsRaw s = diffEntry.getValue();
                args.add(new Object[]{
                        monsterId, difficulty, s.level(), s.minHp(), s.maxHp(), s.ac(),
                        s.a1MinD(), s.a1MaxD(), s.a1Th(), s.a2MinD(), s.a2MaxD(), s.a2Th(),
                        s.s1MinD(), s.s1MaxD(), s.s1Th()
                });
            }
        }
        jdbcTemplate.batchUpdate(INSERT_MONSTER_STAT_SQL, args);
        return args.size();
    }

    // -------------------------------------------------------------------
    // skill + skill_param
    // -------------------------------------------------------------------

    private Map<String, Long> importSkills(long gameId, SkillsRawFile skillsFile) {
        List<Object[]> args = new ArrayList<>(skillsFile.skills().size());
        for (Map.Entry<String, Map<String, String>> entry : skillsFile.skills().entrySet()) {
            // Normalizuje se OUTER KLIC mapy (presne jak to dela Python
            // loader.py: `for k, rec in skills.items(): nk = normalize_key(k)`),
            // ne pole "skill" uvnitr zaznamu - i kdyz se v datech shoduji,
            // drzime se stejneho zdroje jako referencni implementace.
            String rawName = entry.getKey();
            Map<String, String> f = entry.getValue();

            String skillKey = NormalizeKey.normalize(rawName);
            String rawSummon = f.get("summon");
            String monsterKey = isBlank(rawSummon) ? null : NormalizeKey.normalize(rawSummon);
            // Pole "skill" ve zdroji vzdy odpovida vnejsimu klici mapy
            // (overeno pri kontrole dat pred psanim importeru), ale kdyby
            // nekdy chybelo, spadneme zpet na klic mapy misto NULL v
            // NOT NULL sloupci name.
            String name = blankToNull(f.get("skill"));
            if (name == null) {
                name = rawName;
            }

            args.add(new Object[]{
                    gameId,
                    skillKey,
                    parseInt(f.get("*Id"), rawName, "*Id"),
                    name,
                    blankToNull(f.get("charclass")),
                    blankToNull(f.get("skilldesc")),
                    parseShort(f.get("reqlevel"), rawName, "reqlevel"),
                    parseShort(f.get("maxlvl"), rawName, "maxlvl"),
                    blankToNull(f.get("EType")),
                    parseInt(f.get("EMin"), rawName, "EMin"),
                    parseInt(f.get("EMax"), rawName, "EMax"),
                    parseInt(f.get("EMinLev1"), rawName, "EMinLev1"),
                    parseInt(f.get("EMinLev2"), rawName, "EMinLev2"),
                    parseInt(f.get("EMinLev3"), rawName, "EMinLev3"),
                    parseInt(f.get("EMinLev4"), rawName, "EMinLev4"),
                    parseInt(f.get("EMinLev5"), rawName, "EMinLev5"),
                    parseInt(f.get("EMaxLev1"), rawName, "EMaxLev1"),
                    parseInt(f.get("EMaxLev2"), rawName, "EMaxLev2"),
                    parseInt(f.get("EMaxLev3"), rawName, "EMaxLev3"),
                    parseInt(f.get("EMaxLev4"), rawName, "EMaxLev4"),
                    parseInt(f.get("EMaxLev5"), rawName, "EMaxLev5"),
                    monsterKey,
                    blankToNull(f.get("pettype")),
                    blankToNull(f.get("petmax")),
                    parseShort(f.get("HitShift"), rawName, "HitShift"),
                    blankToNull(f.get("calc1")),
                    blankToNull(f.get("aurastat1")),
                    blankToNull(f.get("aurastatcalc1")),
                    parseShort(f.get("SrcDam"), rawName, "SrcDam"),
            });
        }
        jdbcTemplate.batchUpdate(INSERT_SKILL_SQL, args);

        Map<String, Long> idByKey = new HashMap<>(args.size() * 2);
        // POZOR: "rs -> idByKey.put(...)" (bez slozenych zavorek) tady
        // NEJDE prelozit - javac hlasi "reference to query is ambiguous"
        // mezi query(String, ResultSetExtractor<T>, Object...) a
        // query(String, RowCallbackHandler, Object...). Duvod: Map.put()
        // vraci predchozi hodnotu (Long), takze lambda bez slozenych
        // zavorek je SOUCASNE platna jako "vrat Long" (ResultSetExtractor)
        // i jako "nevracej nic" (RowCallbackHandler) - a javac mezi
        // dvema stejne platnymi přetíženimi nedokaze vybrat. Slozene
        // zavorky + strednik (blokove telo bez return) lambdu omezi jen
        // na "nevracej nic" varientu, cimz jednoznacnost obnovi. Explicitni
        // cast na RowCallbackHandler je tu navic, pro čitelnost - dokumentuje
        // zamer primo v kode, ne jen v komentari.
        jdbcTemplate.query(
                SELECT_SKILL_IDS_SQL,
                (RowCallbackHandler) rs -> {
                    idByKey.put(rs.getString("skill_key"), rs.getLong("id"));
                },
                gameId);
        return idByKey;
    }

    private int importSkillParams(SkillsRawFile skillsFile, Map<String, Long> skillIdByKey) {
        List<Object[]> args = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : skillsFile.skills().entrySet()) {
            String skillKey = NormalizeKey.normalize(entry.getKey());
            Long skillId = skillIdByKey.get(skillKey);
            if (skillId == null) {
                throw new DatasetImportException("Skill '" + entry.getKey() + "' nema pridelene ID po importu");
            }
            Map<String, String> f = entry.getValue();

            for (int idx = 1; idx <= 12; idx++) {
                String rawValue = f.get("Param" + idx);
                String description = blankToNull(f.get("*Param" + idx + " Description"));
                Integer value = parseInt(rawValue, entry.getKey(), "Param" + idx);
                if (value == null && description == null) {
                    // ~73 % kombinaci (value, description) je u kazdeho
                    // skillu prazdnych (Param6..12 vetsina skillu vubec
                    // nepouziva) - radek se pak nevklada vubec, misto
                    // 429x12 = 5148 radku jich vznikne ~1391.
                    continue;
                }
                args.add(new Object[]{skillId, (short) idx, value, description});
            }
        }
        jdbcTemplate.batchUpdate(INSERT_SKILL_PARAM_SQL, args);
        return args.size();
    }

    // -------------------------------------------------------------------
    // dataset_version + game
    // -------------------------------------------------------------------

    private long resolveGameId() {
        try {
            return jdbcTemplate.queryForObject(SELECT_GAME_ID_SQL, Long.class, gameCode);
        } catch (EmptyResultDataAccessException e) {
            throw new DatasetImportException(
                    "Hra s kodem '" + gameCode + "' neni v tabulce game - "
                            + "zkontroluj, ze V1 migrace probehla (mela vlozit radek 'd2r').", e);
        }
    }

    private boolean alreadyImported(long gameId, String contentHash) {
        Integer count = jdbcTemplate.queryForObject(COUNT_DATASET_VERSION_SQL, Integer.class, gameId, contentHash);
        return count != null && count > 0;
    }

    private void recordDatasetVersion(long gameId, String excelDir, String contentHash) {
        jdbcTemplate.update(INSERT_DATASET_VERSION_SQL, gameId, sourceVersion, excelDir, contentHash);
    }

    // -------------------------------------------------------------------
    // nacitani a parsovani
    // -------------------------------------------------------------------

    /**
     * Přeloží holé jméno souboru na {@link Resource} podle
     * {@code game-helper.dataset.path}.
     *
     * <p><b>Proč se hodnota property NEPOUŽÍVÁ jako Spring "resource
     * location"</b> (tedy {@code ResourceLoader.getResource(location)}, kde
     * by prefix {@code classpath:}/{@code file:} rozhodl sám): Spring u
     * řetězce bez prefixu zkusí nejdřív URL a při neúspěchu spadne zpátky
     * na CLASSPATH. Jenže {@code D:\data\d2r} - tedy přesně to, co na
     * Windows uživatel do property napíše - vypadá jako URL se schématem
     * {@code D:}, takže by se chování lišilo podle disku a písmene, a
     * {@code /home/user/data} by se tiše hledalo na classpath a "nenašlo".
     * Explicitní pravidlo je nudnější, ale předvídatelné na obou
     * platformách: <b>prázdno = classpath, cokoli jiného = adresář na
     * disku.</b> Kdo chce jiné classpath umístění, přebalí jar.
     */
    private Resource datasetResource(String fileName) {
        if (datasetDir == null) {
            return new ClassPathResource(CLASSPATH_DATASET_DIR + "/" + fileName);
        }
        return new FileSystemResource(datasetDir.resolve(fileName));
    }

    /**
     * Které z {@link #REQUIRED_FILES} nejsou k dispozici. Prázdný seznam =
     * dataset je kompletní. {@code Resource.exists()} je tu jen "dá se to
     * otevřít" - o obsahu nic netvrdí, ten se validuje až při parsování
     * (a tam už chyba fatální JE, viz javadoc třídy).
     */
    private List<String> missingDatasetFiles() {
        List<String> missing = new ArrayList<>();
        for (String fileName : REQUIRED_FILES) {
            if (!datasetResource(fileName).exists()) {
                missing.add(fileName);
            }
        }
        return missing;
    }

    /**
     * WARN, ne INFO ani ERROR. INFO by v běžném startovacím logu zaniklo mezi
     * Hibernate a Flyway hláškami, ERROR tvrdí "něco se pokazilo" - tady se
     * ale nic nepokazilo, jen aplikace běží v omezeném režimu. WARN je přesně
     * "běží, ale asi jsi chtěl něco jiného".
     *
     * <p>Bez diakritiky, stejně jako ostatní log hlášky v téhle třídě -
     * konzole na Windows umí mít jiné kódování než UTF-8 a rozsypaný návod
     * "jak si opravit start" je horší než žádný.
     */
    private void logMissingDataset(List<String> missing) {
        log.warn("""
                        Dataset nenalezen - chybi {} z {} souboru: {}.
                        Hledano v: {}
                        Aplikace nastartuje s PRAZDNOU databazi; endpointy budou vracet prazdne vysledky.
                        Herni data D2R nejsou soucasti repozitare - vygeneruj si je ze sve vlastni instalace hry:
                          python scripts/rebuild_dataset.py
                        a pak na vysledny adresar ukaz property 'game-helper.dataset.path'
                        (promenna prostredi GAME_HELPER_DATASET_PATH). Detaily: backend/README.md.""",
                missing.size(), REQUIRED_FILES.size(), missing, datasetLocationDescription());
    }

    /**
     * Lidsky čitelné "kde se hledalo" do WARN hlášky výš. Absolutní cesta
     * schválně - relativní cesta v property se vyhodnocuje vůči pracovnímu
     * adresáři procesu, který je jinde při {@code mvnw spring-boot:run}
     * (backend/) a jinde v kontejneru (/app), a "nenašel jsem to v ./data"
     * je hláška, po které se hledá dvakrát dýl.
     */
    private String datasetLocationDescription() {
        if (datasetDir == null) {
            return "classpath:" + CLASSPATH_DATASET_DIR
                    + "/ (vychozi - property 'game-helper.dataset.path' neni nastavena)";
        }
        return datasetDir.toAbsolutePath() + " (z property 'game-helper.dataset.path')";
    }

    private byte[] readDatasetBytes(String fileName) {
        Resource resource = datasetResource(fileName);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new DatasetImportException("Nepodarilo se precist " + resource.getDescription(), e);
        }
    }

    private <T> T readJson(String fileName, Class<T> type) {
        return parseJson(readDatasetBytes(fileName), type);
    }

    private <T> T parseJson(byte[] bytes, Class<T> type) {
        try {
            return objectMapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new DatasetImportException("Nepodarilo se rozparsovat JSON typu " + type.getSimpleName(), e);
        }
    }

    /**
     * SHA-256 přes {@code skillsBytes} a {@code monstersBytes} spojené za
     * sebou (v tomhle pořadí) - viz komentář u {@code dataset_version} ve
     * V1 migraci. Je to identita "co jsme naimportovali", ne "z čeho to
     * bylo vyexportované" (to nese {@code excel_dir}, textová
     * dokumentace bez vlivu na hash).
     */
    private static String sha256Hex(byte[] skillsBytes, byte[] monstersBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(skillsBytes);
            digest.update(monstersBytes);
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 je soucasti kazde JVM (JDK garantuje jeho dostupnost
            // v kazde implementaci od specifikace Java Cryptography
            // Architecture) - tahle vetev je nedosazitelna v praxi, ale
            // checked vyjimka si vyzaduje osetreni.
            throw new IllegalStateException("SHA-256 neni dostupne v teto JVM", e);
        }
    }

    /**
     * Vrací {@code null} pro chybějící/prázdnou hodnotu, jinak {@code int}.
     * Na nečíselnou (ale neprázdnou) hodnotu spadne nahlas s kontextem
     * (název skillu + název sloupce) - skenem celého datasetu před psaním
     * importeru bylo ověřeno, že se to na dnešních datech stát nemůže;
     * kdyby se to změnilo v budoucím refreshi, chceme hlasitou chybu při
     * startu, ne tichou nulu.
     */
    private static Integer parseInt(String raw, String skillName, String fieldName) {
        String s = blankToNull(raw);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new DatasetImportException(
                    "Skill '" + skillName + "': pole '" + fieldName + "' = '" + raw + "' neni cele cislo", e);
        }
    }

    private static Short parseShort(String raw, String skillName, String fieldName) {
        Integer value = parseInt(raw, skillName, fieldName);
        return value == null ? null : value.shortValue();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }
}
