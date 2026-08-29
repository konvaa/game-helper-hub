package cz.libor.gamehelper.monster;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Převádí {@link Difficulty} na sloupec {@code monster_stat.difficulty}
 * (TEXT s CHECK 'normal'/'nightmare'/'hell') a zpět.
 *
 * <p><b>Proč ne prosté {@code @Enumerated(EnumType.STRING)}:</b> ten by do
 * databáze zapsal přesně {@code name()} enumu, tedy "NORMAL"/"NIGHTMARE"/
 * "HELL" velkými písmeny. Java konvence pro konstanty enumu je ALL_CAPS a
 * měnit ji kvůli databázi by byl špatný nápad (enum by přestal vypadat jako
 * enum). Databázový CHECK ale byl navržen s malými písmeny - PLAN_P1
 * sekce 4.4 - protože tak je zapsaná obtížnost všude jinde v projektu
 * (Python CLI bere {@code --difficulty hell}, JSON klíče v monsters_base.json
 * jsou "normal"/"nightmare"/"hell"). Converter je přesně nástroj na tenhle
 * nesoulad: nechá Javu mít idiomatický enum a databázi mít svou konvenci,
 * a překlad mezi nimi je na jediném místě.
 *
 * <p><b>{@code autoApply} záměrně není zapnuté.</b> S ním by se converter
 * použil automaticky na KAŽDÉ pole typu {@code Difficulty} v celé aplikaci,
 * aniž by to bylo u pole vidět - "spooky action at a distance". Místo toho
 * je converter uvedený explicitně na poli přes {@code @Convert}
 * (viz {@link MonsterStat#difficulty}), takže je z entity samotné jasné,
 * že se něco převádí.
 */
@Converter
public class DifficultyConverter implements AttributeConverter<Difficulty, String> {

    @Override
    public String convertToDatabaseColumn(Difficulty attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Difficulty convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : Difficulty.valueOf(dbValue.toUpperCase(Locale.ROOT));
    }
}
