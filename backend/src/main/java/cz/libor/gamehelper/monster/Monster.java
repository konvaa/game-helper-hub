package cz.libor.gamehelper.monster;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Identita monstra (z {@code monstats.txt} / {@code monsters_base.json}).
 * Staty per obtížnost jsou v {@link MonsterStat} - viz "proč dvě tabulky"
 * níž u pole {@link #id}.
 *
 * <p><b>Žádná {@code @OneToMany<MonsterStat>} kolekce tady záměrně není.</b>
 * Bidirekční vztah entity → kolekce dětí je klasický zdroj problémů v JPA:
 * lazy kolekce mimo transakci spadne na {@code LazyInitializationException},
 * eager kolekce udělá N+1 při načtení seznamu monster. Místo procházení
 * objektového grafu servisní vrstva v kroku 4 zavolá
 * {@code MonsterStatRepository.findByMonsterId(id)} explicitně - je to o
 * jeden řádek navíc a přesně víš, kdy se který dotaz spustí.
 */
@Entity
@Table(name = "monster")
public class Monster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Plain sloupec, ne {@code @ManyToOne<Game>}. Game entita v projektu
     * zatím vůbec neexistuje - v jedné hře ji nikdo nikdy nenačítá, natož
     * aby procházel vztah zpátky. Pravidlo, které v projektu držíme:
     * cizí klíč se stává objektovým vztahem (@ManyToOne) až ve chvíli, kdy
     * ho kód skutečně potřebuje načíst jako objekt - do té doby je to jen
     * číslo, a DB integritu hlídá "references game(id)" v migraci samotné.
     */
    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "monster_key", nullable = false, unique = true)
    private String monsterKey;

    @Column(name = "name_str")
    private String nameStr;

    /**
     * Bezparametrický konstruktor vyžadovaný JPA specifikací - Hibernate
     * instanci vytváří reflexí (přes proxy) a teprve pak do polí zapisuje
     * hodnoty přečtené z databáze, taky reflexí. Proto tahle třída nemá
     * jediný {@code final} field: reflexe nastavuje hodnoty AŽ PO
     * konstrukci, a {@code final} pole jde nastavit jen v konstruktoru.
     *
     * <p>{@code protected}, ne {@code public}: signalizuje, že konstruktor
     * je pro Hibernate, ne pro běžný kód. Aplikační kód (importer v kroku 3)
     * použije prázdný veřejný konstruktor přes {@code new Monster()} stejně
     * dobře - {@code protected} tady slouží spíš jako dokumentace záměru
     * než jako skutečné omezení přístupu (Hibernate to reflexí obejde vždy).
     */
    protected Monster() {
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public String getMonsterKey() {
        return monsterKey;
    }

    public void setMonsterKey(String monsterKey) {
        this.monsterKey = monsterKey;
    }

    public String getNameStr() {
        return nameStr;
    }

    public void setNameStr(String nameStr) {
        this.nameStr = nameStr;
    }

    // Bez equals()/hashCode(). Vědomé rozhodnutí, ne přehlédnutí: entita se
    // zatím nikdy nedává do Set ani neporovnává v kolekcích - a "správné"
    // equals/hashCode pro JPA entitu je samo o sobě rozsáhlé téma (PK je
    // před uložením null, business klíč může být mutable). Řešit se to bude,
    // až se objeví skutečná potřeba, ne teď dopředu.
}
