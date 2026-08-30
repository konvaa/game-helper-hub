package cz.libor.gamehelper.skill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cz.libor.gamehelper.common.NotFoundException;
import cz.libor.gamehelper.common.PagedResponse;
import cz.libor.gamehelper.skill.dto.SkillDetailDto;
import cz.libor.gamehelper.skill.dto.SkillSummaryDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} - na rozdíl od {@code SkillRepositoryIT} (krok 4)
 * tenhle test NEPOTŘEBUJE databázi. Zvedne se jen webová vrstva (Spring
 * MVC, Jackson, {@code ApiExceptionHandler}) a {@link SkillService} je
 * nahrazený mockem ({@code @MockitoBean}) - testuje se JEN to, co dělá
 * {@link SkillController}: tvar JSON, HTTP status, jak se volá servisní
 * vrstva. Proto tenhle test NEMÁ {@code @Tag("it")} a běží v běžném
 * {@code mvnw test} - nepotřebuje běžící Postgres.
 *
 * <p><b>{@code @MockitoBean}, ne {@code @MockBean}:</b> {@code @MockBean}
 * je od Spring Boot 3.4 zastaralé (deprecated) - starší návody/tutoriály
 * ho pořád používají, ale na 3.5.16 (co používáme) by šlo přeložit jen
 * s varováním. {@code @MockitoBean} (Spring Framework 6.2+) je jeho
 * nástupce se stejným účelem: nahradí bean v testovacím kontextu
 * Mockito mockem.
 */
@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillService skillService;

    @Test
    void listVraciJsonVeTvaruPagedResponse() throws Exception {
        var summary = new SkillSummaryDto("raise_skeleton", "Raise Skeleton", "nec", (short) 1, (short) 20);
        var page = new PagedResponse<>(List.of(summary), 0, 20, 1, 1);
        // any(Pageable.class), ne konkretni PageRequest - presne jaky
        // Pageable Spring z query parametru sestavi je detail, ktery uz
        // overuje SkillRepositoryIT (krok 4). Tenhle test overuje jen, ze
        // controller predal charClass/q servisni vrstve a vratil, co
        // dostal zpet - ne jak presne Spring interne stavi Pageable.
        given(skillService.search(eq("nec"), eq("skel"), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/skills").param("charClass", "nec").param("q", "skel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content[0].key").value("raise_skeleton"))
                .andExpect(jsonPath("$.content[0].name").value("Raise Skeleton"))
                .andExpect(jsonPath("$.content[0].charClass").value("nec"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getByKeyVraciDetailJako200() throws Exception {
        var detail = new SkillDetailDto(
                "raise_skeleton", "Raise Skeleton", "nec", (short) 1, (short) 20,
                "necroskeleton", "sk21", "(lvl < 4) ?lvl:(2+lvl/3)", "chr",
                new SkillDetailDto.ScalingDto(0, 0, 1, 2, 3, 4),
                new SkillDetailDto.ScalingDto(0, 0, 1, 2, 3, 4));
        given(skillService.getByKey("raise_skeleton")).willReturn(detail);

        mockMvc.perform(get("/api/skills/raise_skeleton"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("raise_skeleton"))
                .andExpect(jsonPath("$.monsterKey").value("necroskeleton"))
                .andExpect(jsonPath("$.minScaling.lev5").value(4))
                .andExpect(jsonPath("$.maxScaling.base").value(0));
    }

    @Test
    void getByKeyNeexistujiciKlicVrati404JakoProblemDetail() throws Exception {
        given(skillService.getByKey("neexistuje"))
                .willThrow(new NotFoundException("Skill 'neexistuje' not found"));

        mockMvc.perform(get("/api/skills/neexistuje"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Skill 'neexistuje' not found"))
                .andExpect(jsonPath("$.instance").value("/api/skills/neexistuje"));
    }
}
