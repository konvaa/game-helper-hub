package cz.libor.gamehelper.skill;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Přístup k datům skillů. Spring Data JPA vygeneruje implementaci tohohle
 * rozhraní za běhu (dynamická proxy) - nikde neexistuje žádná
 * {@code SkillRepositoryImpl} třída, natož ruční SQL kolem CRUD operací.
 *
 * <p><b>[Python → Java]</b> V Pythonu (SQLAlchemy) bys dotaz typicky psal
 * explicitně ({@code session.query(...)} nebo {@code select(...)}). Spring
 * Data jde o krok dál: {@link #findBySkillKey(String)} je jen JMÉNO metody -
 * Spring Boot ho při startu rozparsuje ({@code findBy} + název pole
 * {@code SkillKey}) a sám vygeneruje {@code WHERE skill_key = ?}. To funguje,
 * dokud je dotaz jednoduchý; jakmile potřebuješ podmíněné filtry (viz
 * {@link #search}), přechází se na {@code @Query} s JPQL napsaným ručně -
 * derived query naming by se pro dva volitelné parametry zvrhlo v
 * nečitelný název metody.
 */
public interface SkillRepository extends JpaRepository<Skill, Long> {

    /**
     * Odvozený dotaz (derived query) pro {@code GET /api/skills/{key}}
     * (krok 5). {@code skill_key} je {@code UNIQUE} (V2 migrace), takže
     * nejvýš jeden výsledek - proto {@code Optional<Skill>}, ne
     * {@code List<Skill>}. Prázdný {@code Optional} = skill s tímhle klíčem
     * neexistuje, servisní vrstva to v kroku 5 přemapuje na 404.
     */
    Optional<Skill> findBySkillKey(String skillKey);

    /**
     * Filtr pro {@code GET /api/skills?charClass=&q=&page=&size=&sort=}
     * (PLAN_P1_SPRING_SLICE.md sekce 4.5 a 5.1). Obě podmínky jsou
     * VOLITELNÉ - filtrovat podle třídy, podle jména, podle obojího, nebo
     * podle ničeho (jen stránkovat celý seznam). {@code charClass} i
     * {@code q} smí být {@code null}.
     *
     * <p><b>Proč {@code (:x IS NULL OR sloupec = :x)}, a ne Specification /
     * Criteria API:</b> pro DVA volitelné parametry je dynamické skládání
     * dotazu (Spring Data {@code Specification}, QueryDSL) zbytečná vrstva
     * navíc. JPQL idiom "{@code (:charClass IS NULL OR s.charClass =
     * :charClass)}" totéž zvládne v jednom statickém řetězci a Hibernate ho
     * přeloží do jednoho SQL dotazu, kde podmínku vyhodnotí databáze, ne
     * Java. Specification/Criteria API má smysl, až přibude filtrů víc
     * (řekněme 5+) a jejich kombinace by JPQL znečitelnily - tady by to bylo
     * řešení problému, který nemáme.
     *
     * <p><b>Proč {@code LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:q AS
     * string), '%'))}, a ne {@code s.name LIKE ...}:</b> hledání má být
     * case-insensitivní (uživatel zadá "skel", má najít i "Skeleton"). Levá
     * strana {@code LOWER(s.name)} musí být PŘESNĚ výraz, nad kterým je
     * postavený trigramový index {@code idx_skill_name_trgm} (V2 migrace:
     * {@code gin (lower(name) gin_trgm_ops)}) - kdyby se lišila (např. jen
     * {@code s.name}), Postgres by index nemohl použít a dělal by sekvenční
     * průchod přes celou tabulku. Na 429 řádcích to dnes stejně nepoznáš -
     * plánovač si při tak malé tabulce klidně vybere sekvenční průchod sám,
     * protože je to reálně rychlejší než hrabat se v indexu - ale dotaz je
     * napsaný přesně tak, jak by MUSEL vypadat i při 429 000 řádcích. Ověř
     * si to sám: {@code EXPLAIN ANALYZE SELECT * FROM skill WHERE
     * lower(name) LIKE lower('%skel%');} v psql.
     *
     * <p><b>{@code CAST(:q AS string)} NENÍ kosmetika - bez něj tenhle
     * dotaz na {@code q = null} padá</b> na {@code function lower(bytea)
     * does not exist} (opraveno až po nahlášení, viz BACKEND_ZAPISNIK.md
     * "Krok 4 - oprava CAST"). Parametr {@code :q} se jinde v dotazu
     * objevuje jen v {@code :q IS NULL} (typově neutrální srovnání) - na
     * rozdíl od {@code :charClass}, který má nezávisle na své hodnotě
     * jasný typ z {@code s.charClass = :charClass} (sloupec je
     * {@code String}), takže Hibernate nemá odkud spolehlivě odvodit,
     * jakým JDBC typem se má {@code :q} svázat, když je {@code null}. Bez
     * explicitního castu to může vyjít dobře i špatně podle toho, jak
     * zrovna Hibernate tuhle konkrétní variantu dotazu zkompiluje - je to
     * tichá časovaná bomba, ne spolehlivé chování. {@code CAST(... AS
     * string)} dá Hibernate jednoznačný typ bez ohledu na hodnotu za
     * běhu. OBECNÉ PRAVIDLO pro příště: kdykoli je parametr u
     * {@code (:x IS NULL OR ...)} idiomu použitý JINDE v dotazu jen uvnitř
     * řetězcové funkce ({@code CONCAT}, {@code LOWER}, {@code UPPER},
     * {@code SUBSTRING}, ...), a ne v přímém srovnání se sloupcem, je
     * potřeba ho v JPQL přetypovat explicitně - jinak typ visí na tom, co
     * si Hibernate/PostgreSQL driver zrovna domyslí.
     *
     * <p>Bez explicitní {@code countQuery} - Spring Data si pro stránkování
     * (potřebuje znát {@code totalElements}) umí odvodit {@code SELECT
     * COUNT(s) FROM Skill s WHERE ...} sama z hlavního dotazu, protože tenhle
     * dotaz nemá JOIN, GROUP BY ani DISTINCT (tam by odvození bylo
     * nespolehlivé a count dotaz by se musel napsat ručně vedle).
     *
     * <p>Řazení ({@code sort=name,asc} z URL, krok 5) se sem NEPÍŠE do
     * JPQL - Spring Data ho připojí samo z {@link Pageable#getSort()} při
     * sestavování finálního SQL. Kdyby tenhle dotaz měl vlastní
     * {@code ORDER BY}, obě řazení by se sečetla a výsledek by byl matoucí.
     */
    @Query("""
            SELECT s FROM Skill s
            WHERE (:charClass IS NULL OR s.charClass = :charClass)
              AND (:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Skill> search(@Param("charClass") String charClass,
                        @Param("q") String q,
                        Pageable pageable);
}
