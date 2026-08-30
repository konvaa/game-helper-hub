package cz.libor.gamehelper.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import cz.libor.gamehelper.monster.Difficulty;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Jedno místo, kde se výjimky ze servisní vrstvy převádí na HTTP odpověď -
 * {@code @RestControllerAdvice} zachytí výjimku vyhozenou z KTERÉHOKOLI
 * {@code @RestController} v aplikaci (funguje napříč balíčky {@code skill}
 * i {@code monster}), takže controllery samotné žádný try/catch nemají a
 * vrací jen doménová data (PLAN_P1_SPRING_SLICE.md sekce 5.3).
 *
 * <p><b>{@link ProblemDetail} (RFC 9457, {@code application/problem+json}),
 * ne vlastní {@code ApiError} DTO:</b> je to standard, který Spring 6 umí
 * sestavit i serializovat sám - stačí vrátit {@code ProblemDetail} z
 * {@code @ExceptionHandler} metody a Spring podle {@link
 * ProblemDetail#getStatus()} nastaví i HTTP status kód odpovědi (netřeba
 * {@code ResponseEntity<ProblemDetail>} ani {@code @ResponseStatus}
 * navíc). Vlastní {@code ApiError} record by dělal přesně tohle, jen
 * bychom si museli sami definovat tvar, který RFC 9457 už definuje.
 *
 * <p><b>Proč {@code extends ResponseEntityExceptionHandler}:</b> tahle
 * Springí základní třída už MÁ {@code @ExceptionHandler} metody pro
 * standardní MVC výjimky (špatně sestavený request, neplatný typ parametru
 * v cestě, ...) a vrací je taky jako {@code ProblemDetail} - zděděním se
 * tahle konzistence získá zadarmo, bez psaní vlastního handleru pro každou
 * z nich. Sem přidáváme handler pro NAŠI {@link NotFoundException} a
 * (krok 6, oprava) přetížení {@link #handleMethodArgumentNotValid} pro
 * validační chyby {@code @Valid} requestů.
 *
 * <p>{@code instance} v odpovědi (viz příklad v plánu, sekce 5.3) se
 * nedoplní samo - {@link ProblemDetail#forStatusAndDetail} ho nechá
 * prázdné, proto se sem předává {@link HttpServletRequest} jen kvůli
 * {@code getRequestURI()}.
 *
 * <h2>Krok 6, oprava: proč {@code raiseSkeletonLevel=0} i
 * {@code difficulty="nesmysl"} vracely stejné "Invalid request content."</h2>
 *
 * <p>Než tahle třída měla vlastní {@link #handleMethodArgumentNotValid}
 * přetížení, obě chyby padaly do VÝCHOZÍ implementace zděděné z
 * {@link ResponseEntityExceptionHandler} - a ta pro
 * {@link MethodArgumentNotValidException} sestaví {@code ProblemDetail} s
 * obecnou, neinformativní hláškou v {@code detail}, beze zmínky KTERÉ pole
 * a PROČ. Odtud ta identická hláška u dvou různých chyb.
 *
 * <p><b>Oprava předpokladu ze zadání:</b> "Bean Validation u čísla, Jackson
 * deserializace u enumu" by platilo, KDYBY {@code ComputeRequest.difficulty}
 * bylo typu {@code Difficulty} (Java enum) - pak by Jackson při čtení JSON
 * tělesa neplatnou hodnotu odmítl HNED při deserializaci, ještě před Bean
 * Validation, a vyhodil by úplně jinou výjimku
 * ({@code HttpMessageNotReadableException}, obalující Jacksonovu
 * {@code InvalidFormatException}). Ale {@code ComputeRequest.difficulty} je
 * záměrně {@code String} s {@code @Pattern} (viz jeho javadoc - konzistence
 * s {@code MonsterDto.DifficultyStatsDto}, kde je obtížnost taky string, ne
 * enum, napříč celým API). Deserializace {@code String} pole nikdy neselže
 * na OBSAHU řetězce - "nesmysl" se do {@code difficulty} přečte v pořádku,
 * a teprve {@code @Pattern} ho odmítne AŽ v Bean Validation kroku, úplně
 * stejně jako {@code @Min} odmítne {@code raiseSkeletonLevel=0}. Obě chyby
 * jsou proto ve skutečnosti STEJNÝ typ výjimky
 * ({@link MethodArgumentNotValidException}) - odtud stačí přetížit JEDEN
 * handler, ne dva. Jackson-úrovňová chyba (typicky {@code "raiseSkeletonLevel": "abc"}
 * - řetězec tam, kde JSON čeká číslo) je pořád reálná a pořád by dostala tu
 * generickou hlášku - vědomě to necháváme tak, dokud o to Libor nepožádá
 * (jiná výjimka, jiný handler, jiný rozsah opravy).
 *
 * <h2>Standardní pole ProblemDetail, nebo RFC 9457 rozšíření?</h2>
 *
 * <p>RFC 9457 §3.2 výslovně počítá s tím, že konkrétní "problem type" může
 * mít VLASTNÍ členy navíc k pěti standardním ({@code type}, {@code title},
 * {@code status}, {@code detail}, {@code instance}) - přesně pro tenhle
 * případ (více souvisejících chyb v jedné odpovědi). Zvažované varianty:
 * <ul>
 *   <li><b>Všechno napěchovat do {@code detail}</b> (jeden dlouhý string,
 *       např. "raiseSkeletonLevel: musí být alespoň 1; difficulty: musí
 *       být normal/nightmare/hell") - zamítnuto. {@code detail} je podle
 *       RFC 9457 human-readable text, ne strukturovaná data - klient by ho
 *       musel parsovat regexem/split, křehké a nestandardní.</li>
 *   <li><b>Rozšíření přes {@link ProblemDetail#setProperty}</b> - zvoleno.
 *       Spring 6 to podporuje přímo (extra klíč {@code "errors"} se
 *       serializuje jako další top-level JSON pole vedle standardní pětice),
 *       je to oficiálně zamýšlený mechanismus RFC 9457 pro přesně tenhle
 *       účel, a odpověď zůstává validní {@code application/problem+json} -
 *       klient, co dané rozšíření nezná, může pole ignorovat a pořád
 *       dostane use­ful {@code detail}/{@code status}.</li>
 * </ul>
 * Vědomě NEpřidávám vlastní {@code type} URI pro validační chyby (zůstává
 * výchozí {@code "about:blank"}, stejně jako u {@link #handleNotFound}) -
 * bylo by to sice ještě o kousek blíž duchu RFC 9457 (klient by mohl
 * rozlišovat podle {@code type}, ne jen podle HTTP statusu), ale zavedlo by
 * to nekonzistenci mezi dvěma handlery v týž souboru bez toho, aby o to
 * bylo požádáno - necháváme jako otevřenou možnost do budoucna, ne řešíme
 * teď mimo zadání.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Přípustné hodnoty pole {@code difficulty} - odvozené ze
     * {@link Difficulty}, ne nadrátované podruhé, ať existuje jediný zdroj
     * pravdy pro "co je to obtížnost" v celé appce. POZOR: {@code @Pattern}
     * regexp na {@code ComputeRequest.difficulty} (Bean Validation
     * anotace) MUSÍ zůstat ručně v souladu s tímhle výčtem - Java vyžaduje,
     * aby atributy anotací byly compile-time konstanty, takže regexp nejde
     * sestavit dynamicky z {@link Difficulty#values()}. Přidáš-li čtvrtou
     * obtížnost do {@link Difficulty}, uprav i regexp v
     * {@code ComputeRequest}.
     */
    private static final List<String> ALLOWED_DIFFICULTIES = Arrays.stream(Difficulty.values())
            .map(value -> value.name().toLowerCase(Locale.ROOT))
            .toList();

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /**
     * Přetížení zděděné metody z {@link ResponseEntityExceptionHandler} -
     * NE nový {@code @ExceptionHandler} (na rozdíl od
     * {@link #handleNotFound}). {@link MethodArgumentNotValidException} už
     * má svoji obsluhu v rodičovské třídě (proto ji dědíme, viz javadoc
     * třídy) - abychom ji ZMĚNILI, musíme přesně tuhle metodu přetížit, ne
     * přidat další handler vedle (dva handlery na stejnou výjimku by Spring
     * odmítl při startu jako nejednoznačnou konfiguraci).
     *
     * <p><b>{@code @Override} je tu záměrně, ne kosmetika:</b> kdybych
     * podpis metody netrefil přesně (Spring Framework 6 tu má
     * {@link HttpStatusCode}, ne starší {@code HttpStatus} - je to jedna z
     * věcí, co bys měl při spuštění potvrdit, protože jde o framework API,
     * které tady nemám jak zkompilovat naostro), {@code @Override} donutí
     * {@code javac} nahlásit chybu překladu HNED ("method does not override
     * method from its superclass") - bez něj by se překlad tiše povedl, jen
     * by vznikla NESOUVISEJÍCÍ přetížená metoda, kterou by Spring nikdy
     * nezavolal, a viděl bys pořád tu starou obecnou hlášku, bez jakékoli
     * chyby, která by tě na to upozornila. Kdyby build spadl přesně na
     * "does not override" u týhle metody, je to signál, že se podpis mezi
     * verzemi Spring Frameworku liší - napiš mi přesné znění chyby.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<FieldViolationDto> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle("Neplatný request");
        problem.setDetail("Request neprošel validací (" + violations.size()
                + (violations.size() == 1 ? " neplatné pole" : " neplatných polí")
                + ") - podrobnosti v poli \"errors\".");
        problem.setProperty("errors", violations);

        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }

        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    /**
     * Jedno {@link FieldError} (jedno pole, jedno porušené pravidlo) →
     * {@link FieldViolationDto}. {@code allowedValues} se doplní JEN pro
     * {@code difficulty} - je to jediné pole s uzavřenou množinou platných
     * hodnot (obě čísla mají rozsah, ne výčet, takže tam "seznam
     * přípustných hodnot" nedává smysl, zprávu z {@code @Min}/{@code @Max}
     * to už řekne). Schválně rozpoznáno podle JMÉNA pole, ne podle typu
     * anotace ({@code error.getCode()} by vrátilo "Pattern" u
     * {@code difficulty}, ale obecně "je to Pattern" neznamená "existuje
     * konečný seznam hodnot" - regulární výraz obecně enumerovat nejde) -
     * pro jeden request DTO se dvěma poli je tahle jednoduchá vazba čitelnější
     * než obecný mechanismus, který by tu zatím neměl žádné druhé využití.
     */
    private FieldViolationDto toViolation(FieldError error) {
        List<String> allowedValues = "difficulty".equals(error.getField()) ? ALLOWED_DIFFICULTIES : null;
        return new FieldViolationDto(error.getField(), error.getRejectedValue(), error.getDefaultMessage(), allowedValues);
    }

    /**
     * Jeden prvek pole {@code "errors"} v {@code ProblemDetail} rozšíření
     * (viz javadoc třídy, "Standardní pole ProblemDetail, nebo RFC 9457
     * rozšíření?"). {@code @JsonInclude(NON_NULL)} na {@code allowedValues}
     * - u {@code raiseSkeletonLevel}/{@code skeletonMasteryLevel} je
     * {@code null} (viz {@link #toViolation}), a bez týhle anotace by
     * Jackson do JSON napsal {@code "allowedValues":null} u KAŽDÉHO pole,
     * ne jen u {@code difficulty} - zbytečný šum v odpovědi.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldViolationDto(String field, Object rejectedValue, String message, List<String> allowedValues) {
    }
}
