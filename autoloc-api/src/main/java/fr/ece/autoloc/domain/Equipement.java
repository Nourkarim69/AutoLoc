package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Option that can be added to a reservation (GPS, child seat, snow chains...).
 */
@Entity
@Table(name = "equipement")
@Getter
@Setter
@NoArgsConstructor
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEquipement;

    @Column(nullable = false, unique = true, length = 100)
    private String libelle;

    /** Price added per rental day. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifJournalier;

    public Equipement(String libelle, BigDecimal tarifJournalier) {
        this.libelle = libelle;
        this.tarifJournalier = tarifJournalier;
    }
}
