package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicule")
@Getter
@Setter
@NoArgsConstructor
public class Vehicule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVehicule;

    @Column(nullable = false, unique = true, length = 20)
    private String immatriculation;

    @Column(nullable = false, length = 50)
    private String marque;

    @Column(nullable = false, length = 50)
    private String modele;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorieVehicule categorie;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifJournalier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutVehicule statut;

    /**
     * Agency where the car is picked up (0..* vehicles -> 0..1 agency).
     * EAGER (JPA default for @ManyToOne): the agency is always displayed with the car and is a single small row.
     * Nullable: a car can be temporarily attached to no agency.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_agence")
    private Agence agence;

    /**
     * Maintenance history (1 vehicle -> 0..* maintenances), inverse side of Maintenance.vehicule.
     * cascade REMOVE + orphanRemoval: a maintenance record has no meaning without its car (composition).
     * LAZY: only loaded when explicitly needed.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "vehicule", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Maintenance> maintenances = new ArrayList<>();

    public Vehicule(String immatriculation, String marque, String modele, CategorieVehicule categorie,
                    BigDecimal tarifJournalier, StatutVehicule statut, Agence agence) {
        this.immatriculation = immatriculation;
        this.marque = marque;
        this.modele = modele;
        this.categorie = categorie;
        this.tarifJournalier = tarifJournalier;
        this.statut = statut;
        this.agence = agence;
    }
}
