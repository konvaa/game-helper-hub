// Integrační test POST /api/summons/raise-skeleton/compute přes MockMvc.
// Na rozdíl od SkillControllerTest se NEmockuje servisní vrstva, ale až
// repozitáře: controller -> SummonService -> RaiseSkeletonCalculator běží
// naostro a nahrazená je jen databáze. Tak test ověří celý řetězec
// (validace, výběr řádku podle obtížnosti, výpočet, tvar JSON, ProblemDetail)
// a přitom nepotřebuje Postgres ani dataset - běží v obyčejném "mvnw test"
// i v CI. Plný @SpringBootTest by vyžadoval databázi (Flyway při startu).
//
// Jméno SummonControllerTest už předjímá javadoc RaiseSkeletonCalculatorTest.
// Suffix "Test", ne "IT": v tomhle projektu "IT" znamená @Tag("it") = potřebuje
// běžící databázi (viz pom.xml), a to tenhle test nepotřebuje.

package cz.libor.gamehelper.summon;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cz.libor.gamehelper.monster.Difficulty;
import cz.libor.gamehelper.monster.Monster;
import cz.libor.gamehelper.monster.MonsterRepository;
import cz.libor.gamehelper.monster.MonsterStat;
import cz.libor.gamehelper.monster.MonsterStatRepository;
import cz.libor.gamehelper.skill.Skill;
import cz.libor.gamehelper.skill.SkillRepository;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest(SummonController.class)} zvedne jen webovou vrstvu
 * (MVC, Jackson, Bean Validation, {@code ApiExceptionHandler}).
 * {@link SummonService} je {@code @Service}, do tohohle "řezu" kontextu
 * sám nepatří - proto {@code @Import}. Repozitáře jsou {@code @MockitoBean}.
 *
 * <p><b>[Python → Java]</b> MockMvc neposílá skutečný HTTP request přes síť
 * (na rozdíl třeba od {@code requests} proti běžícímu serveru, nebo Flask
 * {@code test_client}, který je MockMvc nejblíž) - volá DispatcherServlet
 * přímo v paměti. Proto je rychlý a nepotřebuje volný port.
 */
@WebMvcTest(SummonController.class)
@Import(SummonService.class)
class SummonControllerTest {

    private static final String URL = "/api/summons/raise-skeleton/compute";
    private static final long MONSTER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillRepository skillRepository;

    @MockitoBean
    private MonsterRepository monsterRepository;

    @MockitoBean
    private MonsterStatRepository monsterStatRepository;

    /**
     * Entity mají {@code protected} konstruktor (požadavek JPA) a
     * {@code Monster} nemá setter na {@code id} - proto Mockito mocky
     * místo {@code new}. Reflexe by šla taky, ale mock je čitelnější
     * a nastavuje jen to, co služba opravdu čte.
     *
     * <p>Hell řádek = skutečné staty necroskeletona (stejné jako
     * v RaiseSkeletonCalculatorTest). Normal a nightmare jsou SYNTETICKÁ
     * čísla, ne herní data - reálné hodnoty v repozitáři nejsou a tady
     * stačí, že se od hell liší, aby šlo poznat, který řádek služba vybrala.
     */
    @BeforeEach
    void datovaSada() {
        Skill skill = mock(Skill.class);
        given(skill.getMonsterKey()).willReturn("necroskeleton");
        given(skill.getEMin()).willReturn(0);
        given(skill.getEMinLev1()).willReturn(0);
        given(skill.getEMinLev2()).willReturn(1);
        given(skill.getEMinLev3()).willReturn(2);
        given(skill.getEMinLev4()).willReturn(3);
        given(skill.getEMinLev5()).willReturn(4);
        given(skillRepository.findBySkillKey("raise_skeleton")).willReturn(Optional.of(skill));

        Monster monster = mock(Monster.class);
        given(monster.getId()).willReturn(MONSTER_ID);
        given(monsterRepository.findByMonsterKey("necroskeleton")).willReturn(Optional.of(monster));

        // Seznam se sestaví PŘED given(...): stat() uvnitř sám stubuje, a
        // rozdělané stubování uvnitř jiného given() Mockito odmítne
        // (UnfinishedStubbingException).
        List<MonsterStat> stats = List.of(
                stat(Difficulty.NORMAL, 100, 10, 20, 50, 70),
                stat(Difficulty.NIGHTMARE, 200, 30, 40, 60, 80),
                stat(Difficulty.HELL, 42, 1, 2, 6, 6));
        given(monsterStatRepository.findByMonsterId(MONSTER_ID)).willReturn(stats);
    }

    private static MonsterStat stat(Difficulty difficulty, int maxHp, int minD, int maxD, int ac, int th) {
        MonsterStat stat = mock(MonsterStat.class);
        given(stat.getDifficulty()).willReturn(difficulty);
        given(stat.getMaxHp()).willReturn(maxHp);
        given(stat.getA1MinD()).willReturn(minD);
        given(stat.getA1MaxD()).willReturn(maxD);
        given(stat.getAc()).willReturn(ac);
        given(stat.getA1Th()).willReturn(th);
        return stat;
    }

    // --- úspěšné výpočty ---

    /**
     * Baseline případ rs20_sm11 (stejný jako příklad v backend/README.md).
     * Ověřuje celý tvar odpovědi: {@code input}, {@code monsterId},
     * {@code final} (v Javě {@code finalValues}, viz ComputeResponse) a
     * {@code breakdown}.
     */
    @Test
    void platnyRequestVratiVypocetVeTvaruComputeResponse() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "hell"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.input.raiseSkeletonLevel").value(20))
                .andExpect(jsonPath("$.input.skeletonMasteryLevel").value(11))
                .andExpect(jsonPath("$.input.difficulty").value("hell"))
                .andExpect(jsonPath("$.monsterId").value(MONSTER_ID))
                .andExpect(jsonPath("$.final.hp").value(487))
                .andExpect(jsonPath("$.final.physMin").value(85))
                .andExpect(jsonPath("$.final.physMax").value(87))
                .andExpect(jsonPath("$.final.defense").value(471))
                .andExpect(jsonPath("$.final.attackRating").value(471))
                .andExpect(jsonPath("$.final.count").value(8))
                // "finalValues" se do JSON dostat nesmí - @JsonProperty("final") ho přejmenuje.
                .andExpect(jsonPath("$.finalValues").doesNotExist())
                .andExpect(jsonPath("$.breakdown.length()").value(2))
                .andExpect(jsonPath("$.breakdown[0].label").value("rs_internal_flat"))
                .andExpect(jsonPath("$.breakdown[0].value").value(16.0))
                .andExpect(jsonPath("$.breakdown[1].label").value("total_skill_bonus"))
                .andExpect(jsonPath("$.breakdown[1].value").value(38.0));
    }

    /**
     * Obtížnost je case-insensitive ({@code (?i)} v {@code @Pattern}) a do
     * {@code input} se vrací tak, jak ji klient poslal - převod na enum dělá
     * až služba a request DTO nemění.
     */
    @Test
    void obtiznostVelkymiPismenyProjde() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "HELL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input.difficulty").value("HELL"))
                .andExpect(jsonPath("$.final.hp").value(487));
    }

    /**
     * Služba musí vzít řádek monster_stat pro požadovanou obtížnost, ne
     * první v seznamu. Syntetický nightmare řádek (200 HP, dmg 30-40, AC 60,
     * AR 80), rs=1, sm=1: hp = 200 + 0 % + 8 = 208; dmg = 30 + 2 = 32 a
     * 40 + 2 = 42 (na rs=1 bez procent); def = 60 + 2*15 = 90; AR = 80 + 30 = 110.
     */
    @Test
    void pocitaSeStatyZvoleneObtiznosti() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 1, "skeletonMasteryLevel": 1, "difficulty": "nightmare"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final.hp").value(208))
                .andExpect(jsonPath("$.final.physMin").value(32))
                .andExpect(jsonPath("$.final.physMax").value(42))
                .andExpect(jsonPath("$.final.defense").value(90))
                .andExpect(jsonPath("$.final.attackRating").value(110));
    }

    /** Horní hranice validace (99) ještě musí projít - viz ComputeRequest, proč ne 20. */
    @Test
    void uroven99JeJestePlatna() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 99, "skeletonMasteryLevel": 99, "difficulty": "hell"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final.count").value(35));
    }

    // --- nevalidní vstup ---

    /**
     * Příklad z backend/README.md: dvě chybná pole, dvě položky v "errors".
     * {@code verifyNoInteractions} dokazuje, že validace proběhla dřív, než
     * se sáhlo do databáze - to README tvrdí ("funguje i bez datasetu").
     */
    @Test
    void neplatnaUrovenAObtiznostVrati400SeDvemaChybami() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 0, "skeletonMasteryLevel": 11, "difficulty": "nesmysl"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(URL))
                .andExpect(jsonPath("$.errors.length()").value(2))
                // Pořadí FieldErrorů Bean Validation negarantuje - proto filtr podle pole, ne index.
                .andExpect(jsonPath("$.errors[?(@.field == 'raiseSkeletonLevel')].rejectedValue").value(0))
                .andExpect(jsonPath("$.errors[?(@.field == 'raiseSkeletonLevel')].allowedValues").doesNotExist())
                .andExpect(jsonPath("$.errors[?(@.field == 'difficulty')].rejectedValue").value("nesmysl"))
                .andExpect(jsonPath("$.errors[?(@.field == 'difficulty')].allowedValues[*]")
                        .value(Matchers.containsInAnyOrder("normal", "nightmare", "hell")));

        verifyNoInteractions(skillRepository, monsterRepository, monsterStatRepository);
    }

    /** Prázdné tělo {} - všechna tři pole chybí, každé má vlastní @NotNull/@NotBlank chybu. */
    @Test
    void chybejiciPoleVrati400ProKazdePole() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder(
                        "raiseSkeletonLevel", "skeletonMasteryLevel", "difficulty")));
    }

    @Test
    void uroven100JeNadMaximem() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 100, "skeletonMasteryLevel": 11, "difficulty": "hell"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("raiseSkeletonLevel"))
                .andExpect(jsonPath("$.errors[0].message").value("raiseSkeletonLevel může být nejvýš 99"));
    }

    /**
     * Text místo čísla spadne už v Jacksonu (HttpMessageNotReadableException),
     * ne v Bean Validation - dostane obecný ProblemDetail zděděný
     * z ResponseEntityExceptionHandler, BEZ pole "errors". Je to vědomý stav
     * popsaný v javadocu ApiExceptionHandler, test ho jen fixuje.
     */
    @Test
    void textMistoCislaVrati400BezPoleErrors() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": "abc", "skeletonMasteryLevel": 11, "difficulty": "hell"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void rozbityJsonVrati400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"raiseSkeletonLevel\": 20,"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void jinyContentTypeNezJsonVrati415() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("raiseSkeletonLevel=20"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // --- chybějící data v databázi ---

    /** Bez datasetu (README: "výpočet summonu vrátí 404") - skill v DB není. */
    @Test
    void chybejiciSkillVDatabaziVrati404() throws Exception {
        given(skillRepository.findBySkillKey(any())).willReturn(Optional.empty());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "hell"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Skill 'raise_skeleton' nenalezen - chybí v datové sadě?"));
    }

    @Test
    void chybejiciRadekProObtiznostVrati404() throws Exception {
        List<MonsterStat> onlyHell = List.of(stat(Difficulty.HELL, 42, 1, 2, 6, 6));
        given(monsterStatRepository.findByMonsterId(MONSTER_ID)).willReturn(onlyHell);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 20, "skeletonMasteryLevel": 11, "difficulty": "normal"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Monster 7 nemá staty pro obtížnost 'normal'"));
    }

    // --- nalezená chyba (neopraveno, viz @Disabled) ---

    /**
     * Skeleton Mastery 0 je ve hře platný stav (nekromant do ní nemusí dát
     * ani bod) a referenční baseline ho obsahuje: případ rs40_sm00_hell
     * v scripts/tests/baseline_raise_skeleton_highlvl.json. Kalkulátor ho
     * spočítá správně (RaiseSkeletonCalculatorBoundaryTest), ale
     * ComputeRequest má na skeletonMasteryLevel {@code @Min(1)}, takže API
     * vrátí 400. Očekávané hodnoty jsou z té baseline.
     */
    @Disabled("Nalezená chyba, záměrně neopravená: ComputeRequest.skeletonMasteryLevel má @Min(1), "
            + "ale Skeleton Mastery 0 je platný vstup (baseline rs40_sm00_hell) - API vrací 400. "
            + "Oprava = @Min(0) + úprava message a @Schema(minimum); rozhodnutí nechávám na autorovi.")
    @Test
    void skeletonMasteryNulaJePlatnyVstup() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"raiseSkeletonLevel": 40, "skeletonMasteryLevel": 0, "difficulty": "hell"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final.hp").value(819))
                .andExpect(jsonPath("$.final.physMin").value(312))
                .andExpect(jsonPath("$.final.physMax").value(315))
                .andExpect(jsonPath("$.final.defense").value(606))
                .andExpect(jsonPath("$.final.attackRating").value(606))
                .andExpect(jsonPath("$.final.count").value(15));
    }
}
