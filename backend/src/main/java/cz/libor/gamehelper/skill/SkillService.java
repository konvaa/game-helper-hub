package cz.libor.gamehelper.skill;

import cz.libor.gamehelper.common.NotFoundException;
import cz.libor.gamehelper.common.PagedResponse;
import cz.libor.gamehelper.skill.dto.SkillDetailDto;
import cz.libor.gamehelper.skill.dto.SkillSummaryDto;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servisní vrstva mezi {@link SkillController} a {@link SkillRepository}.
 * Balíčkově privátní (bez {@code public}) - podle pravidla z
 * PLAN_P1_SPRING_SLICE.md sekce 2.2, funkční balíček {@code skill} drží
 * svoje interní věci mimo dosah ostatních balíčků. Volá ji jen
 * {@code SkillController}, který je ve STEJNÉM balíčku {@code
 * cz.libor.gamehelper.skill} - proto {@code package-private} stačí.
 *
 * <p><b>{@code @Transactional(readOnly = true)} na celé třídě</b> - obě
 * metody jsou čtení, žádná nezapisuje. Hibernate uvnitř transakce
 * označené {@code readOnly} vypne dirty checking (nemusí před koncem
 * transakce kontrolovat, jestli se načtené entity změnily) a neudělá
 * flush - u čistě čtecí operace je to čistý zisk bez rizika (PLAN_P1
 * sekce 3.1, pravidlo 2 pro JPA na read-only datech).
 */
@Service
@Transactional(readOnly = true)
class SkillService {

    private final SkillRepository skillRepository;

    // Jediný konstruktor => Spring ho použije pro dependency injection
    // automaticky, i bez @Autowired na něm (od Spring 4.3). @Autowired by
    // tu nic nepřidalo, jen by opakovalo to, co je jasné z kontextu.
    SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * {@code GET /api/skills?charClass=&q=&page=&size=&sort=}. Normalizace
     * vstupu (prázdný řetězec → {@code null}, {@code charClass} na malá
     * písmena) je ZÁMĚRNĚ tady, ne v {@link SkillRepository#search} - ten
     * dotaz je čistá databázová otázka ("filtruj podle přesně TÉHLE
     * hodnoty nebo nefiltruj vůbec"), zatímco "jak z uživatelského vstupu
     * z URL udělat tu přesnou hodnotu" je byznysová/aplikační logika a
     * patří do servisní vrstvy - stejné dělení odpovědnosti jako u
     * {@code blankToNull} v importeru (krok 3).
     */
    PagedResponse<SkillSummaryDto> search(String charClass, String q, Pageable pageable) {
        Page<Skill> page = skillRepository.search(normalizeCharClass(charClass), blankToNull(q), pageable);
        return PagedResponse.from(page.map(SkillSummaryDto::from));
    }

    /**
     * {@code GET /api/skills/{key}}. {@link SkillRepository#findBySkillKey}
     * vrací {@code Optional} - "možná tu není", ne výjimku - protože na
     * úrovni repozitáře je "skill neexistuje" úplně normální výsledek
     * dotazu. Až TADY, na hranici mezi doménou a API, se z toho stává
     * chyba ({@link NotFoundException}), kterou {@code
     * ApiExceptionHandler} převede na {@code 404}.
     */
    SkillDetailDto getByKey(String key) {
        return skillRepository.findBySkillKey(key)
                .map(SkillDetailDto::from)
                .orElseThrow(() -> new NotFoundException("Skill '" + key + "' not found"));
    }

    private static String normalizeCharClass(String charClass) {
        String trimmed = blankToNull(charClass);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
