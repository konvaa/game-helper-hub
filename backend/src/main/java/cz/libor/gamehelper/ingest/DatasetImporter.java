package cz.libor.gamehelper.ingest;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Spustí import datasetu při startu aplikace - JEDNOU, po plné inicializaci
 * kontextu (a tedy i po tom, co Flyway aplikuje migrace).
 *
 * <p><b>Proč {@code ApplicationRunner}, ne {@code @PostConstruct}.</b>
 * {@code @PostConstruct} běží v okamžiku, kdy Spring inicializuje TENHLE
 * konkrétní bean - to může být dřív, než je hotový celý kontext (a určitě
 * dřív, než proběhnou Flyway migrace, které Boot spouští jako součást
 * autokonfigurace DataSource). {@code ApplicationRunner.run()} se naopak
 * volá AŽ PO úplném nastartování kontextu - typicky poslední krok před tím,
 * než je aplikace "ready". Výjimka odtud aplikaci korektně shodí (exit kód
 * != 0), místo aby doběhla do částečně nekonzistentního stavu.
 *
 * <p>Tahle třída je záměrně tenká - žádná {@code @Transactional} logika tu
 * není. Důvod je v {@link DatasetImportService#importIfNeeded()} javadocu:
 * transakční metoda musí být volaná z JINÉHO beanu, aby se uplatnila
 * Spring proxy.
 */
@Component
public class DatasetImporter implements ApplicationRunner {

    private final DatasetImportService importService;

    public DatasetImporter(DatasetImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        importService.importIfNeeded();
    }
}
