package cz.libor.gamehelper.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Port {@code normalize_key()} z Python enginu
 * ({@code scripts/summon_engine/loader.py}). MUSÍ dávat bit-shodný výstup -
 * je to jediná definice toho, co znamená "stejný klíč" napříč oběma
 * implementacemi, a na tom stojí:
 * <ul>
 *   <li>{@code skill.skill_key} v URL ({@code GET /api/skills/{key}}) -
 *       musí odpovídat tomu, co by vrátil Python CLI pro stejný skill;</li>
 *   <li>join {@code skill.monster_key -> monster.monster_key} - klíč
 *       monstra v {@code monsters_base.json} NENÍ vždy už normalizovaný
 *       (viz {@link #normalize} javadoc níž) a bez normalizace na OBOU
 *       stranách by import 9 z 752 monster nenašel přes {@code summon}
 *       odkaz vůbec.</li>
 * </ul>
 *
 * <p><b>Ověřeno na datech před psaním importeru</b> (ne odhadem): žádný
 * ze 429 skillů aktuálně neodkazuje summonem na jeden z těch 9 problémových
 * klíčů (jsou to pasti a "Expansion"/"qual-kehk", ne pety), takže riziko je
 * dnes latentní, ne aktivní. Symetrická normalizace (na monster i skill
 * straně) navíc opravuje i druhý, aktivně používaný případ - 4 golemí
 * skilly mají {@code summon} v PascalCase ("ClayGolem"), zatímco
 * {@code monsters_base.json} má klíč malými písmeny ("claygolem"). Ověřeno
 * skriptem: po aplikaci {@code normalize_key} na obě strany se najde všech
 * 35 neprázdných {@code summon} odkazů a mezi 752 monstry nevznikne po
 * normalizaci kolize (752 unikátních vstupů -&gt; 752 unikátních klíčů).
 *
 * <p><b>[Python → Java] Proč Java verze je robustnější než ta Python:</b>
 * {@code loader.py: get_monster()} zkouší normalizovaný klíč a při neúspěchu
 * spadne na fallback přes {@code casefold()} - který ale řeší jen VELIKOST
 * PÍSMEN, ne pomlčku vs. podtržítko. Pro těch 9 problémových klíčů (s
 * pomlčkou) by ten fallback taky selhal, kdyby na ně někdy nějaký skill
 * odkázal. Tady normalizujeme OBĚ strany stejnou funkcí při importu (ne až
 * při dotazu), takže tahle mezera nemůže nastat.
 */
public final class NormalizeKey {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private NormalizeKey() {
        // Utility třída - jen statická metoda, instance nedávají smysl.
    }

    /**
     * @param raw syrová hodnota (název skillu, klíč monstra z JSON, ...).
     *            {@code null} a prázdný/whitespace-only řetězec vrací "".
     * @return normalizovaný klíč: ořezaný, pomlčky nahrazené mezerou,
     *         vícenásobné mezery sloučené, mezery nahrazené podtržítkem,
     *         malými písmeny.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replace('-', ' ');
        s = WHITESPACE_RUN.matcher(s).replaceAll(" ");
        s = s.replace(' ', '_');
        return s.toLowerCase(Locale.ROOT);
    }
}
