package cz.libor.gamehelper.summon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.libor.gamehelper.summon.RaiseSkeletonResult;
import java.util.List;

/**
 * Odpověď {@code POST /api/summons/raise-skeleton/compute}
 * (PLAN_P1_SPRING_SLICE.md sekce 5.1):
 * {@code { input, monsterId, final:{...}, breakdown:[...] }}.
 *
 * <p><b>Proč {@code finalValues}, a v JSON přesto {@code "final"}:</b>
 * {@code final} je v Javě rezervované klíčové slovo - nejde ho použít jako
 * jméno recordové komponenty (na rozdíl od Pythonu, kde {@code final} žádný
 * zvláštní status nemá a klidně by šlo jako jméno pole). Java pole se proto
 * jmenuje {@code finalValues} a {@link JsonProperty} řekne Jacksonu, ať ho
 * v JSON přesto serializuje/deserializuje jako {@code "final"} - přesně tvar,
 * který plán předepisuje. Anotace na komponentě recordu se automaticky
 * promítne na pole, getter i konstruktorový parametr, takže stačí jednou.
 *
 * <p><b>{@code breakdown}:</b> pole {@code {label, value}} dvojic - dnes
 * jen {@code rs_internal_flat} a {@code total_skill_bonus} (ty samé dvě
 * "extract" hodnoty, které ověřuje {@code RaiseSkeletonCalculatorTest} proti
 * baseline). Smysl: frontend může bez další logiky vypsat "z čeho se číslo
 * skládá", aniž by musel počítat totéž znovu na klientovi.
 */
public record ComputeResponse(
        ComputeRequest input,
        Long monsterId,
        @JsonProperty("final") FinalDto finalValues,
        List<BreakdownItemDto> breakdown) {

    public record FinalDto(int hp, int physMin, int physMax, int defense, int attackRating, int count) {
    }

    public record BreakdownItemDto(String label, double value) {
    }

    public static ComputeResponse from(ComputeRequest input, Long monsterId, RaiseSkeletonResult result) {
        FinalDto finalDto = new FinalDto(
                result.hp(), result.physMin(), result.physMax(),
                result.defense(), result.attackRating(), result.count());

        List<BreakdownItemDto> breakdown = List.of(
                new BreakdownItemDto("rs_internal_flat", result.rsInternalFlat()),
                new BreakdownItemDto("total_skill_bonus", result.totalSkillBonus()));

        return new ComputeResponse(input, monsterId, finalDto, breakdown);
    }
}
