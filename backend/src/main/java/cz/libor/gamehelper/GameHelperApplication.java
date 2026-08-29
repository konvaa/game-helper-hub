package cz.libor.gamehelper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Vstupní bod aplikace.
 *
 * <p>{@code @SpringBootApplication} je zkratka za tři anotace:
 * <ul>
 *   <li>{@code @SpringBootConfiguration} - tahle třída smí definovat beany
 *       ({@code @Bean} metody).</li>
 *   <li>{@code @EnableAutoConfiguration} - zapne autokonfiguraci: Boot projde
 *       classpath a podle toho, co na ní najde, nastartuje odpovídající části
 *       (našel PostgreSQL driver a spring.datasource.url -&gt; vyrobí DataSource;
 *       našel Flyway -&gt; spustí migrace před inicializací Hibernate).</li>
 *   <li>{@code @ComponentScan} - prohledá <b>tenhle balíček a všechny pod ním</b>
 *       a zaregistruje třídy označené {@code @Component}, {@code @Service},
 *       {@code @Repository}, {@code @RestController}, ... jako beany.</li>
 * </ul>
 *
 * <p><b>Proto tahle třída sedí v kořeni {@code cz.libor.gamehelper}</b> a ne
 * v podbalíčku. Kdyby byla např. v {@code cz.libor.gamehelper.config},
 * scan by začal tam a všechno v {@code skill/}, {@code monster/} a
 * {@code summon/} by zůstalo neviditelné. Je to nejčastější důvod hlášky
 * "No qualifying bean of type ... available" u začínajícího projektu.
 *
 * <p><b>Poznámka k dependency injection:</b> beany se do sebe zapojují přes
 * konstruktor - třída si v konstruktoru vyžádá, co potřebuje, a Spring jí to
 * podá. V celém projektu důsledně používáme konstruktorovou injektáž, nikdy
 * {@code @Autowired} na poli. Důvody: pole jde udělat {@code final} (objekt je
 * po vytvoření kompletní a neměnný), třída jde vytvořit v testu obyčejným
 * {@code new} bez Spring kontextu, a když má konstruktor osm parametrů, je na
 * první pohled vidět, že třída dělá moc věcí - u pole schované v anotaci to
 * vidět není.
 */
@SpringBootApplication
public class GameHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameHelperApplication.class, args);
    }
}
