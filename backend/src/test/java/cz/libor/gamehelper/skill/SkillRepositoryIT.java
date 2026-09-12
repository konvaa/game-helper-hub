package cz.libor.gamehelper.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Integrační test {@link SkillRepository#search} - na rozdíl od
 * {@code NormalizeKeyTest} (čistý JUnit, žádný Spring kontext) tenhle test
 * potřebuje běžící databázi s naimportovanými daty, protože ověřuje SQL,
 * které Spring Data teprve za běhu vygeneruje z JPQL v repozitáři. Bez
 * skutečné databáze bychom testovali jen to, že se aplikace zkompilovala -
 * ne že dotaz vrací, co má.
 *
 * <p><b>{@code @Tag("it")} - proč tenhle test NEBĚŽÍ v běžném
 * {@code mvnw test}:</b> PLAN_P1_SPRING_SLICE.md sekce 7.4 řeší přesně
 * tohle - "mvn test" musí být zelený i bez běžícího Dockeru, protože kdo si
 * repo naklonuje (třeba náborář), nesmí dostat červený build jen proto, že
 * nemá spuštěnou databázi. {@code pom.xml} proto defaultně vylučuje testy
 * se štítkem {@code it} a spustí se jen přes {@code mvnw test -Pit} - kdy
 * MUSÍ běžet {@code docker compose up -d db} s naimportovaným datasetem
 * (jak ho dodat, popisuje {@code backend/README.md}). Past při
 * pojmenování třídy: Surefire soubory {@code *IT.java} ve výchozím
 * nastavení VŮBEC nenačítá - proto má {@code pom.xml} explicitní
 * {@code <includes>}, viz komentář u surefire pluginu tam.
 *
 * <p><b>Proč tenhle test míří na reálnou databázi z docker-compose, a ne na
 * Testcontainers</b> (jak sekce 7.2/7.4 plánu navrhovaly jako výchozí
 * cestu): Testcontainers je ve slice vědomě odložený na krok 8 a přidal by
 * novou závislost jen kvůli jednomu testu. {@code @DataJpaTest} defaultně
 * zkouší nahradit datový zdroj vestavěnou databází (H2/Derby) - tu ale
 * NEMÁME na classpath, a ani by to nešlo použít: schéma používá
 * {@code pg_trgm} rozšíření a GIN trigramový index, což jsou
 * PostgreSQL-specifické věci, které H2 neumí. {@code
 * @AutoConfigureTestDatabase(replace = NONE)} řekne Springu "nenahrazuj
 * nic, použij datasource z application.yml" - tedy TU SAMOU databázi
 * z {@code docker compose up}, kterou už máš spuštěnou a naplněnou
 * z kroku 3.
 *
 * <p>Nevýhoda tohohle přístupu: test čte reálná importovaná data z
 * {@code skills_raw.json}, ne izolované fixture, které si sám připraví a
 * uklidí. U jednorázového, staticky bundlovaného datasetu (jedna hra, jedna
 * verze) je to vědomý kompromis, ne přehlédnutí - kdyby se dataset časem
 * měnil (nová verze D2R), čísla v assercích níž by se musela přepočítat.
 *
 * <p>Všechna čísla v testech jsou ověřená skriptem nad {@code
 * skills_raw.json} a reálným dotazem proti Postgresu (stejná metodika jako
 * u importeru v kroku 3), ne odhadnutá.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("it")
class SkillRepositoryIT {

    @Autowired
    private SkillRepository skillRepository;

    @Test
    void findBySkillKeyNajdeRaiseSkeleton() {
        var found = skillRepository.findBySkillKey("raise_skeleton");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Raise Skeleton");
        assertThat(found.get().getCharClass()).isEqualTo("nec");
    }

    @Test
    void findBySkillKeyNeexistujiciKlicVratiPrazdnyOptional() {
        assertThat(skillRepository.findBySkillKey("tohle-neexistuje")).isEmpty();
    }

    @Test
    void searchBezFiltruVratiVsechnySkilly() {
        var page = skillRepository.search(null, null, PageRequest.of(0, 500));

        // 429 - počet skillů, které importer vloží z plného datasetu D2R 3.3.
        assertThat(page.getTotalElements()).isEqualTo(429);
    }

    @Test
    void searchPodleTridyVratiJenNecromancerSkilly() {
        var page = skillRepository.search("nec", null, PageRequest.of(0, 100));

        // Ověřeno skriptem nad skills_raw.json: 7 hratelných tříd po 30
        // skillech, zbylých 189 ze 429 má charclass NULL (obecné a
        // monster-only skilly - V2 migrace, komentář u idx_skill_charclass).
        assertThat(page.getTotalElements()).isEqualTo(30);
    }

    @Test
    void searchFulltextNajdeVsechnySkelSkillyNapricTridami() {
        var page = skillRepository.search(null, "skel", PageRequest.of(0, 100));

        // Ověřeno skriptem: "skel" (case-insensitive, KDEKOLI v řetězci) je
        // podřetězec ve čtyřech jménech napříč celou hrou, ne jen u nec -
        // SkeletonRaise je monster-only skill (charclass NULL).
        assertThat(page.getContent())
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder(
                        "Raise Skeletal Mage", "Raise Skeleton", "Skeleton Mastery", "SkeletonRaise");
    }

    @Test
    void searchFulltextJeCaseInsensitive() {
        var maleQ = skillRepository.search(null, "skel", PageRequest.of(0, 100));
        var velkeQ = skillRepository.search(null, "SKEL", PageRequest.of(0, 100));

        // Ověřuje LOWER() na obou stranách LIKE - bez něj by "SKEL" nenašlo
        // nic, protože sloupec obsahuje "Skeleton" s malým zbytkem slova.
        assertThat(velkeQ.getTotalElements()).isEqualTo(maleQ.getTotalElements());
    }

    @Test
    void searchKombinujeTriduAFulltext() {
        var page = skillRepository.search("nec", "skel", PageRequest.of(0, 100));

        // Průnik: ze čtyř "skel" skillů má charclass='nec' jen tři -
        // SkeletonRaise (monster-only) do průniku nepatří. Přesně tenhle
        // případ (OBĚ podmínky najednou) je důvod, proč tohle testovat
        // repozitářovým testem, ne jen ručně v psql - ověřuje se tím
        // chování Spring Data + Hibernate při skládání dotazu, ne SQL
        // samotné.
        assertThat(page.getContent())
                .extracting(Skill::getName)
                .containsExactlyInAnyOrder("Raise Skeletal Mage", "Raise Skeleton", "Skeleton Mastery");
    }

    @Test
    void searchRespektujeStrankovaniARazeni() {
        var page = skillRepository.search(null, null, PageRequest.of(0, 5, Sort.by("name").ascending()));

        // Abecedně prvních pět je psáno velkým počátečním písmenem. Databáze
        // z docker-compose.yml (postgres:17-alpine) používá defaultně "C"
        // collation (ASCII pořadí bajtů) - velká písmena řadí PŘED malými,
        // na rozdíl od "přirozeného" řazení, které by dalo 'a' vedle 'A'.
        // Ověřeno reálným dotazem proti Postgresu, ne jen odhadem z Pythonu.
        assertThat(page.getContent())
                .extracting(Skill::getName)
                .containsExactly("Abyss", "Amplify Damage", "AndrialSpray", "AndyPoisonBolt", "Apocalypse");
        assertThat(page.getTotalElements()).isEqualTo(429);
        assertThat(page.getTotalPages()).isEqualTo(86); // ceil(429 / 5)
    }
}
