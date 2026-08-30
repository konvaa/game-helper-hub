package cz.libor.gamehelper.ingest;

/**
 * Nekontrolovaná (unchecked) výjimka pro chyby importu.
 *
 * <p><b>Proč vůbec existuje, místo aby se nechala probublat
 * {@code IOException} nebo {@code NumberFormatException} přímo:</b> tohle
 * NENÍ jen styl. {@link DatasetImportService#importIfNeeded()} je
 * {@code @Transactional} a Spring defaultně dělá rollback jen na
 * NEKONTROLOVANÝCH výjimkách ({@code RuntimeException}/{@code Error}) - u
 * kontrolované výjimky jako {@code IOException} by transakci naopak
 * COMMITL, i kdyby import spadl v polovině. Balení kontrolovaných výjimek
 * (JSON parsing, I/O) do týhle třídy proto není kosmetika, ale podmínka
 * pro to, aby selhání importu opravdu vzalo zpátky všechno, co stihlo
 * vložit - žádná napůl naimportovaná data v databázi.
 */
public class DatasetImportException extends RuntimeException {

    public DatasetImportException(String message) {
        super(message);
    }

    public DatasetImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
