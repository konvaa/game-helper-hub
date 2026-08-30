package cz.libor.gamehelper.summon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Tělo {@code POST /api/summons/raise-skeleton/compute}
 * (PLAN_P1_SPRING_SLICE.md sekce 5.1). Jakarta Bean Validation anotace tu
 * ověří vstup DŘÍV, než se vůbec dostane do {@code SummonController} -
 * Spring MVC s {@code @Valid} (viz controller) vyhodí
 * {@code MethodArgumentNotValidException}, kterou zdědí
 * {@code ApiExceptionHandler} (z {@code ResponseEntityExceptionHandler})
 * a přemapuje na {@code 400} + {@code ProblemDetail} - BEZ jediného řádku
 * navíc v tomhle projektu (krok 5 ApiExceptionHandler žádnou vlastní
 * obsluhu validace nepsal, jen zdědil tu Spring Frameworku). Je to PRVNÍ
 * endpoint v projektu, který {@code @Valid} vůbec použije - přesné chování
 * (tvar JSON chybové odpovědi) zapisuje {@code docs/BACKEND_ZAPISNIK.md},
 * "Krok 6" po ověření.
 *
 * <p><b>{@code @Min(1) @Max(99)}, NE {@code @Max(20)}:</b>
 * PLAN_P1_SPRING_SLICE.md sekce 5.1/{@code SKILLS_PROJECTION.md} 2.5 - 20 je
 * strop INVESTOVANÝCH bodů do skillu (co dovolí hra při levelování postavy),
 * ne strop EFEKTIVNÍ úrovně (tu zvedají itemy/aury/atd. a hra ji nijak
 * neomezuje na 20). Endpoint počítá s efektivní úrovní, proto validace
 * dovolí až 99 - je to vědomá volba, ne nedopatření, a stojí za to ji umět
 * u pohovoru zdůvodnit přesně takhle.
 *
 * <p><b>Proč {@code Integer}, ne primitivní {@code int}:</b> u JSON pole,
 * které v requestu chybí, by Jackson primitivní {@code int} tiše doplnil
 * na {@code 0} (bez chyby) - {@code 0} by ale prošlo přes deserializaci a
 * teprve pak spadlo na {@code @Min(1)}, což funguje, ale zpráva by mluvila
 * o "hodnotě 0", ne o "chybějícím poli". {@code Integer} navíc umožňuje
 * {@code @NotNull} rozlišit "pole chybí" od "pole je nulové" - přesnější
 * chybová hláška pro klienta.
 *
 * <p><b>{@code difficulty} jako {@code String}, ne rovnou
 * {@code Difficulty} enum:</b> API request/response ve zbytku projektu
 * (viz {@code MonsterDto.DifficultyStatsDto}) obtížnost vždy reprezentuje
 * jako malými písmeny psaný string ("hell"), ne Javí enum - konzistence
 * s tím vzorem je tu důležitější než "silnější" typ na hranici HTTP
 * requestu. {@code @Pattern} ověří tvar TADY (400 při překlepu), servisní
 * vrstva string na {@code Difficulty} mapuje AŽ POTÉ, co ví, že je platný.
 */
public record ComputeRequest(

        @NotNull(message = "raiseSkeletonLevel je povinné")
        @Min(value = 1, message = "raiseSkeletonLevel musí být alespoň 1")
        @Max(value = 99, message = "raiseSkeletonLevel může být nejvýš 99")
        Integer raiseSkeletonLevel,

        @NotNull(message = "skeletonMasteryLevel je povinné")
        @Min(value = 1, message = "skeletonMasteryLevel musí být alespoň 1")
        @Max(value = 99, message = "skeletonMasteryLevel může být nejvýš 99")
        Integer skeletonMasteryLevel,

        @NotBlank(message = "difficulty je povinné")
        @Pattern(regexp = "(?i)normal|nightmare|hell", message = "difficulty musí být normal, nightmare nebo hell")
        String difficulty) {
}
