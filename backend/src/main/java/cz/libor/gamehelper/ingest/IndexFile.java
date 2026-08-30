package cz.libor.gamehelper.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Odpovídá {@code dataset/index.json} - provenience exportu. Čteme z něj
 * jen {@code excel_dir}; zbytek ({@code cap}, {@code files}, {@code notes})
 * je pro Python pipeline, importeru je jedno.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} je tu záměrně:
 * bez něj by Jackson vyhodil {@code UnrecognizedPropertyException} na první
 * neznámé pole. Chceme, aby import fungoval, i když Python k
 * {@code index.json} časem přidá další metadata, která Java nepotřebuje.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexFile(
        @JsonProperty("excel_dir") String excelDir
) {
}
