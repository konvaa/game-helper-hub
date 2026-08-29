package cz.libor.gamehelper.monster;

/**
 * Tři herní obtížnosti D2R. Java enum, ne string konstanty - kompilátor
 * odmítne přeložit kód s překlepem ("Hel" místo "Hell"), string by to
 * nechal projít až do běhu.
 *
 * <p><b>[Python → Java]</b> V Pythonu bys tohle nejspíš řešil stringem nebo
 * {@code Literal["normal","nightmare","hell"]} a typová kontrola by byla jen
 * na statickém analyzátoru (mypy), ne vynucená za běhu. Java enum je
 * skutečný typ - {@code Difficulty.HELL} je jediná instance třídy
 * {@code Difficulty} s tímhle jménem, ne řetězec.
 *
 * <p>Jak se converuje na/z databázového textu ('normal'/'nightmare'/'hell')
 * řeší {@link DifficultyConverter} - viz jeho javadoc, proč to není prosté
 * {@code @Enumerated(EnumType.STRING)}.
 */
public enum Difficulty {
    NORMAL,
    NIGHTMARE,
    HELL
}
