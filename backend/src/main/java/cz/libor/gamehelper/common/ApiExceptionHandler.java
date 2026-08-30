package cz.libor.gamehelper.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
 * z nich. Sem přidáváme jen handler pro NAŠI {@link NotFoundException}.
 *
 * <p>{@code instance} v odpovědi (viz příklad v plánu, sekce 5.3) se
 * nedoplní samo - {@link ProblemDetail#forStatusAndDetail} ho nechá
 * prázdné, proto se sem předává {@link HttpServletRequest} jen kvůli
 * {@code getRequestURI()}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
