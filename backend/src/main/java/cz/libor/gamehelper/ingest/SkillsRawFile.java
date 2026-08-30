package cz.libor.gamehelper.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Odpovídá {@code dataset/skills_raw.json}:
 * {@code {"meta": {...}, "skills": {"Raise Skeleton": {...322 poli...}, ...}}}.
 *
 * <p>Vnitřní záznam skillu NENÍ mapovaný na POJO se 322 poli - je to
 * {@code Map<String, String>}, protože je to přesně to, co soubor je: ploché
 * TSV řádky, kde JE VŽDY string (i čísla jsou string, prázdno je {@code ""},
 * ne {@code null}). Ověřeno skenem celého souboru před psaním importeru -
 * nula ne-stringových hodnot v 429×322 polích. Import čte jen těch ~29
 * sloupců, které potřebuje (viz {@link DatasetImportService}), zbytek
 * mapou jednoduše prochází bez povšimnutí.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillsRawFile(
        Map<String, Map<String, String>> skills
) {
}
