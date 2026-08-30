package cz.libor.gamehelper.common;

/**
 * Sdílená výjimka pro "tenhle záznam neexistuje" napříč celou aplikací -
 * {@code SkillService} i {@code MonsterService} (krok 5) ji hází se svou
 * vlastní zprávou, {@link ApiExceptionHandler} ji na jednom místě převádí
 * na {@code 404} + {@code ProblemDetail}.
 *
 * <p><b>Unchecked ({@code extends RuntimeException}), ne checked.</b>
 * Kdyby to byla checked výjimka, každá metoda mezi místem, kde se hodí
 * (repozitář/servisní vrstva), a controllerem, by musela mít
 * {@code throws NotFoundException} v signatuře - i metody, které o
 * existenci záznamu vůbec nerozhodují, jen volání předávají dál. "Záznam
 * nenalezen" je navíc v REST API BĚŽNÝ, ne výjimečný stav (404 je legitimní
 * odpověď, ne selhání aplikace) - přesně proto se hodí nechat ji propadnout
 * až do {@code @RestControllerAdvice}, ne ji řešit checked mechanismem
 * navrženým pro chyby, které volající MUSÍ ošetřit na místě.
 *
 * <p><b>[Python → Java]</b> V Pythonu bys pravděpodobně vyhodil vlastní
 * {@code class NotFoundError(Exception)} a chytal ho v Flask/FastAPI
 * error handleru - stejný vzor, jen v Pythonu jsou VŠECHNY výjimky
 * "unchecked" (není tam ekvivalent Javí checked výjimky), takže tahle
 * volba v Pythonu vůbec nevyvstane.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
