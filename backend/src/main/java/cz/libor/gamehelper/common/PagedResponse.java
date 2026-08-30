package cz.libor.gamehelper.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Vlastní obálka pro stránkovanou odpověď - {@code GET /api/skills} ji
 * vrací místo toho, aby controller poslal Spring Data {@link Page} přímo.
 *
 * <p><b>Proč vlastní record, a ne rovnou {@code Page<T>}:</b>
 * {@code PageImpl} (implementace {@code Page}, kterou Spring Data vrací)
 * má desítky polí navíc (např. {@code pageable}, {@code sort} jako
 * vnořené objekty) - JSON tvar záleží na tom, jak zrovna Jackson serializuje
 * Springí interní třídu, a Spring sám při serializaci {@code PageImpl}
 * loguje varování, že tenhle tvar NENÍ garantovaný napříč verzemi (může se
 * změnit i v minor release). Vlastní {@code PagedResponse} je pět řádků a
 * kontrakt API pak drží náš kód, ne cizí knihovna - klient (frontend,
 * náborář zkoušející endpoint) dostane pokaždé stejný, jednoduchý tvar:
 * {@code content}, {@code page}, {@code size}, {@code totalElements},
 * {@code totalPages}.
 *
 * <p><b>[Python → Java]</b> Generický typ {@code <T>} tady dělá to samé,
 * co bys v Pythonu řešil bez přemýšlení (funkce vracející stránkovaný
 * seznam čehokoliv) - v Javě to musí být vyjádřené explicitně v typovém
 * systému, aby {@code PagedResponse<SkillSummaryDto>} a
 * {@code PagedResponse<MonsterDto>} byly odlišné, typově bezpečné tvary,
 * ne dva stejné "obecné dictionary" jako v Pythonu.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Postaví {@code PagedResponse} ze Spring Data {@link Page}. Statická
     * tovární metoda místo konstruktoru, protože {@code Page} se dá na
     * {@code PagedResponse} převést jen JEDNÍM rozumným způsobem (rozbalit
     * pole) - constructor by musel jmenovat parametr {@code page} a
     * zaměňovat ho s polem {@code page} (číslo aktuální stránky) by bylo
     * matoucí.
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
