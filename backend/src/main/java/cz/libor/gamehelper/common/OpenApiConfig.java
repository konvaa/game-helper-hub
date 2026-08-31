package cz.libor.gamehelper.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata pro OpenAPI dokument, který springdoc sestaví ze skenování
 * {@code @RestController}/{@code @Valid}/{@code @Schema} anotací (krok 7,
 * PLAN_P1_SPRING_SLICE.md sekce 3, řádek "springdoc-openapi-starter-webmvc-ui").
 *
 * <p><b>Proč vlastní {@code @Bean OpenAPI}, a ne jen properties v
 * {@code application.yml}:</b> springdoc umí základní věci (title, verzi)
 * nastavit i přes {@code springdoc.*}/{@code spring.application.name}
 * properties bez jediné řádky Javy - ale jakmile chceš i {@code description}
 * s víc než jednou větou (viz níž), je čitelnější mít to v Javě jako
 * normální text, ne jako YAML multiline string s escapováním.
 *
 * <p>Tahle třída nic neregistruje ručně (žádné {@code @Bean
 * GroupedOpenApi}, žádné vlastní cesty) - springdoc SÁM najde tenhle
 * {@code OpenAPI} bean (podle typu, ne podle jména) a použije ho jako
 * základ, do kterého pak doplní všechny naskenované endpointy. Bez téhle
 * třídy by Swagger UI fungoval úplně stejně, jen s generickým
 * "OpenAPI definition" místo jména a popisu projektu.
 *
 * <p><b>{@code version("0.1.0")} je ručně psaný literál, ne čtený z
 * {@code pom.xml}:</b> "Implementation-Version" z manifestu by fungoval jen
 * po zabalení do jaru ({@code mvn package}) - při běžném lokálním vývoji
 * (`mvnw spring-boot:run`, bez balení) by byl {@code null}. Pro jednu
 * metadatovou hodnotu je jednodušší mít ji tady a aktualizovat ručně při
 * vydání, než řešit oba případy (jar/bez jaru) zvlášť.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gameHelperOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Game Helper API")
                        .version("0.1.0")
                        .description("""
                                REST API nad daty Diablo II: Resurrected - výpočet statů \
                                přivolaných jednotek (summonů) z herních dat a úrovní skillů.

                                P1 slice: katalog skillů a monster (GET), plus výpočet statů \
                                jednoho přivolaného kostlivce (Raise Skeleton, POST) - \
                                8/8 shodných výsledků s referenční Python implementací.

                                Zdroj a zdůvodnění architektury: docs/PLAN_P1_SPRING_SLICE.md \
                                v repozitáři."""));
    }
}
