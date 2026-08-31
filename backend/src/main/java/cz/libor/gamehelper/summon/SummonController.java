package cz.libor.gamehelper.summon;

import cz.libor.gamehelper.summon.dto.ComputeRequest;
import cz.libor.gamehelper.summon.dto.ComputeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/summons/raise-skeleton/compute} - PLAN_P1_SPRING_SLICE.md
 * sekce 5.1, čtvrtý a poslední endpoint P1 slice.
 *
 * <p><b>Proč {@code POST}, a ne {@code GET}:</b> jde o příkaz s tělem
 * requestu (tři vstupní čísla/string), ne o adresovatelný zdroj - to je
 * důvod, který v plánu zvítězil. Protiargument, který stojí za znalost
 * i u pohovoru: {@code GET /api/summons/raise-skeleton?rs=20&sm=11&difficulty=hell}
 * by byl idempotentní, cache-ovatelný a dal by se poslat obyčejným odkazem -
 * výpočet sám o sobě žádný stav nemění, takže by se pro GET kvalifikoval.
 * "Vybral jsem POST kvůli tělu requestu, ale GET by tu taky obstál" je
 * lepší odpověď než "tak se to obvykle dělá".
 *
 * <p>Package-private - stejný důvod jako {@code SkillController}/
 * {@code MonsterController} v kroku 5: Spring si {@code @RestController}
 * najde přes classpath scanning, veřejná viditelnost tu nic nepřidává.
 */
@Tag(name = "Summons", description = "Výpočet statů přivolaných jednotek. P1 slice: jen Raise Skeleton.")
@RestController
@RequestMapping("/api/summons")
class SummonController {

    private final SummonService summonService;

    SummonController(SummonService summonService) {
        this.summonService = summonService;
    }

    /**
     * {@code @Valid} spustí Jakarta Bean Validation anotace z
     * {@link ComputeRequest} DŘÍV, než se tělo metody vůbec spustí - při
     * neplatném vstupu Spring vyhodí {@code MethodArgumentNotValidException}
     * a {@code SummonService.computeRaiseSkeleton} se nezavolá vůbec (viz
     * {@link ComputeRequest} javadoc, jak se tahle výjimka dál mapuje na
     * 400 + {@code ProblemDetail}).
     */
    @Operation(
            summary = "Spočítá staty jednoho přivolaného kostlivce (Raise Skeleton)",
            description = "8/8 shodných výsledků s referenční Python implementací "
                    + "(RaiseSkeletonCalculatorTest, scripts/tests/baseline_raise_skeleton.json). "
                    + "400 s ProblemDetail (pole \"errors\") při neplatném vstupu - viz "
                    + "ApiExceptionHandler.")
    @PostMapping("/raise-skeleton/compute")
    ComputeResponse compute(@Valid @RequestBody ComputeRequest request) {
        return summonService.computeRaiseSkeleton(request);
    }
}
