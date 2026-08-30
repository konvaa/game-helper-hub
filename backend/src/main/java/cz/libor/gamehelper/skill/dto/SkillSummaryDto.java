package cz.libor.gamehelper.skill.dto;

import cz.libor.gamehelper.skill.Skill;

/**
 * Řádek v {@code GET /api/skills} - pět polí, co potřebuje seznam/výpis.
 * Detail (jeden skill) má vlastní, bohatší tvar - {@link SkillDetailDto}.
 *
 * <h2>Proč DTO, a ne vrátit rovnou {@link Skill} (entitu) z controlleru</h2>
 *
 * Tohle je otázka, na kterou má smysl umět odpovědět u pohovoru celou -
 * pět nezávislých důvodů, seřazených podle toho, jak často v praxi reálně
 * kousnou:
 *
 * <ol>
 *   <li><b>Lazy loading.</b> Kdyby entita měla lazy kolekci (např.
 *       {@code @OneToMany}) a Jackson by ji serializoval MIMO transakci
 *       (což se v {@code @RestController} běžně stane - transakce servisní
 *       vrstvy skončí dřív, než controller vrátí odpověď a Jackson ji
 *       převede na JSON), sáhnutí na tu kolekci skončí
 *       {@code LazyInitializationException}. Kdyby byla eager, serializuje
 *       se ti celý objektový graf pod entitou - u seznamu 429 skillů by to
 *       byly stovky zbytečných dotazů (N+1). {@code SkillSummaryDto} žádnou
 *       kolekci nemá, je to jen pět skalárních polí - riziko zmizí, protože
 *       ho DTO strukturálně nemůže mít.</li>
 *
 *   <li><b>Cyklus v grafu.</b> Kdyby byl vztah obousměrný (entita A drží
 *       referenci na B a B zpátky na A), serializace by se točila donekonečna
 *       a spadla by na {@code StackOverflowError}. Naše entity obousměrné
 *       vztahy nemají (viz {@link cz.libor.gamehelper.monster.Monster},
 *       "žádná {@code @OneToMany} kolekce"), ale DTO dělá tenhle problém
 *       principiálně nemožný, ne jen "zrovna teď se nestane".</li>
 *
 *   <li><b>Kontrakt API vs. tvar databáze.</b> Entita je 1:1 obrazem tabulky
 *       {@code skill} (26 sloupců, viz {@code Skill.java}). Kdybychom ji
 *       vraceli přímo, každé přejmenování sloupce nebo změna typu v migraci
 *       by byla breaking change pro každého, kdo API volá - i kdyby s tím
 *       sloupcem API vůbec nepracovalo. DTO je smlouva, kterou měníme
 *       VĚDOMĚ, odděleně od schématu databáze.</li>
 *
 *   <li><b>Tvar podle endpointu, ne podle tabulky.</b> Seznam potřebuje pět
 *       polí, detail třicet (a jinak strukturovaných - viz
 *       {@link SkillDetailDto#minScaling()}). Jedna entita nemůže mít dva
 *       různé JSON tvary zároveň bez berliček jako {@code @JsonView}. Dvě
 *       DTO to vyřeší přirozeně - každé je "tvar pro tenhle jeden
 *       endpoint".</li>
 *
 *   <li><b>Bezpečnost/přesah.</b> Co je v entitě, to se dá (omylem) poslat
 *       ven - včetně sloupců, které ven nepatří. U {@code skill} to dnes
 *       není citlivé, ale princip platí obecně: DTO je EXPLICITNÍ seznam
 *       toho, co ven jde, entita je implicitní "všechno, co tam zrovna je".</li>
 * </ol>
 *
 * <p><b>[Python → Java]</b> V Pythonu bys prostě postavil {@code dict}
 * s tím, co chceš vrátit, a nikdo tomu neřekne "DTO" - je to samozřejmost,
 * ne vzor s vlastním jménem. V Javě je to totéž, jen to má typ:
 * {@code record} (Java 16+) je přesně "neměnný dict s pojmenovanými,
 * typovanými poli" - vygenerovaný konstruktor, {@code equals}/
 * {@code hashCode}/{@code toString} zadarmo. Rozdíl proti Pythonu není
 * filozofický (obojí je "jen vrať to, co klient potřebuje"), je v tom, že
 * Java má lazy proxy entit a kompilovaný, staticky kontrolovaný kontrakt -
 * takže oddělení entity od odpovědi API tady reálně něco řeší, v Pythonu
 * bys na stejný problém nenarazil, protože tam žádné lazy proxy nejsou.
 *
 * <p>Mapování entita → DTO je RUČNÍ statická metoda ({@link #from}), ne
 * MapStruct/ModelMapper (PLAN_P1_SPRING_SLICE.md sekce 5.2) - pro pár DTO
 * je generátor zbytečná závislost, a u pohovoru chceš umět ukázat, že
 * mapování rozumíš, ne že ho za tebe vygenerovala anotace.
 */
public record SkillSummaryDto(
        String key,
        String name,
        String charClass,
        Short reqLevel,
        Short maxLevel) {

    public static SkillSummaryDto from(Skill skill) {
        return new SkillSummaryDto(
                skill.getSkillKey(),
                skill.getName(),
                skill.getCharClass(),
                skill.getReqLevel(),
                skill.getMaxLevel());
    }
}
