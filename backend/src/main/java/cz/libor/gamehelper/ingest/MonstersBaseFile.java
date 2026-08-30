package cz.libor.gamehelper.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Odpovídá {@code dataset/monsters_base.json}. Na rozdíl od
 * {@link SkillsRawFile} tady Python při generování dat UŽ převedl staty na
 * skutečná JSON čísla (viz {@code min_hp: 86}, ne {@code "86"}) - proto tu
 * jde mapovat rovnou na typované recordy, žádné ruční parsování stringů.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MonstersBaseFile(
        Map<String, MonsterRawRecord> monsters
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonsterRawRecord(
            // Zrcadli vnejsi klic mapy (napr. "id": "skeleton1" u klice
            // "skeleton1"). Import pouziva vnejsi klic (Map.Entry.getKey()),
            // ne tohle pole - drzi se to mapovaci konvence pro skill_key
            // v SkillsRawFile. Pole tu zustava, aby record odpovidal
            // skutecnemu tvaru JSON 1:1, ne kvuli pouziti v kode.
            String id,
            @JsonProperty("name_str") String nameStr,
            Map<String, DifficultyStatsRaw> base
    ) {
    }

    /**
     * Jeden záznam z {@code base.normal} / {@code base.nightmare} /
     * {@code base.hell}. Pole odpovídají 1:1 sloupcům
     * {@code monster_stat} - viz V2 migrace pro jejich herní význam
     * (Attack 1 / Attack 2 / Skill 1).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DifficultyStatsRaw(
            Integer level,
            @JsonProperty("min_hp") Integer minHp,
            @JsonProperty("max_hp") Integer maxHp,
            Integer ac,
            @JsonProperty("a1_min_d") Integer a1MinD,
            @JsonProperty("a1_max_d") Integer a1MaxD,
            @JsonProperty("a1_th") Integer a1Th,
            @JsonProperty("a2_min_d") Integer a2MinD,
            @JsonProperty("a2_max_d") Integer a2MaxD,
            @JsonProperty("a2_th") Integer a2Th,
            @JsonProperty("s1_min_d") Integer s1MinD,
            @JsonProperty("s1_max_d") Integer s1MaxD,
            @JsonProperty("s1_th") Integer s1Th
    ) {
    }
}
