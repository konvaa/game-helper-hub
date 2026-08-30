package cz.libor.gamehelper.skill;

import cz.libor.gamehelper.common.PagedResponse;
import cz.libor.gamehelper.skill.dto.SkillDetailDto;
import cz.libor.gamehelper.skill.dto.SkillSummaryDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/skills} - dva GET endpointy z akceptačního kritéria
 * (PLAN_P1_SPRING_SLICE.md sekce 5.1). Controller sám žádnou logiku nemá -
 * jen přijme HTTP request, zavolá {@link SkillService} a vrátí, co dostal.
 * Chyby (404) řeší {@link cz.libor.gamehelper.common.ApiExceptionHandler}
 * o úroveň výš, ne try/catch tady.
 *
 * <p><b>{@code Pageable pageable} jako parametr metody "jen tak funguje"</b>
 * díky auto-konfiguraci Spring Data Webu (zapne se sama, když je na
 * classpath {@code spring-boot-starter-web} i {@code
 * spring-boot-starter-data-jpa} zároveň) - Spring z query parametrů
 * {@code page}, {@code size} a {@code sort} sestaví {@link Pageable} sám,
 * dřív, než se metoda vůbec zavolá. {@code @PageableDefault} nastavuje,
 * co se použije, když request žádné z nich neposlal - výchozích 20 řádků
 * seřazených podle jména odpovídá příkladu v plánu
 * ({@code ?...&size=20&sort=name,asc}).
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public PagedResponse<SkillSummaryDto> list(
            @RequestParam(required = false) String charClass,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return skillService.search(charClass, q, pageable);
    }

    @GetMapping("/{key}")
    public SkillDetailDto getByKey(@PathVariable String key) {
        return skillService.getByKey(key);
    }
}
