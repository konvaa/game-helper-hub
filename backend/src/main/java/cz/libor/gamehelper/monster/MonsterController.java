package cz.libor.gamehelper.monster;

import cz.libor.gamehelper.monster.dto.MonsterDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/monsters/{id}} - jediný GET endpoint z akceptačního
 * kritéria pro monstra. {@code id} je surogátní {@code BIGSERIAL} PK
 * (krok 2), NE {@code monster_key} - proto {@code Long id}, ne
 * {@code String}. Spring sám vrátí {@code 400 Bad Request} (přes
 * standardní {@code MethodArgumentTypeMismatchException}, kterou zdědění
 * {@code ResponseEntityExceptionHandler} v {@code ApiExceptionHandler} umí
 * převést na {@code ProblemDetail}), když někdo pošle
 * {@code /api/monsters/abc} - o tohle se nemusí starat ani controller,
 * ani service.
 */
@RestController
@RequestMapping("/api/monsters")
public class MonsterController {

    private final MonsterService monsterService;

    public MonsterController(MonsterService monsterService) {
        this.monsterService = monsterService;
    }

    @GetMapping("/{id}")
    public MonsterDto getById(@PathVariable Long id) {
        return monsterService.getById(id);
    }
}
