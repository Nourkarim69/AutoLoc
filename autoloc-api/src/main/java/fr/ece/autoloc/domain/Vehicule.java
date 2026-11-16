package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "vehicule")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
     * Agency where the car is picked up (0..* vehicles -> 0..1 agency). Owning side (foreign key id_agence).
     * LAZY: avoids loading chains of objects; the lists that display the agency load it with @EntityGraph.
     * No cascade: a car survives the deletion of its agency. Nullable: a car can be attached to no agency.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agence")
    private Agence agence;

    /**
     * Maintenance history (1 vehicle -> 0..* maintenances), inverse side of Maintenance.vehicule.
     * cascade PERSIST: a new maintenance added to the car is saved with it. No REMOVE: the history is kept,
     * a car that has been in the garage cannot be deleted by mistake (HTTP 409).
     * LAZY: only loaded when explicitly needed.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "vehicule", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<Maintenance> maintenances = new ArrayList<>();

    /**
     * Equipment fitted on the car (* vehicles <-> * equipments). Owning side: join table vehicule_equipement.
     * LAZY (collection), no cascade: the equipment catalogue is shared and must survive the car.
     * A Set avoids duplicates and the "bag" limitations of Hibernate. Read with GET /api/vehicules/{id}/equipements.
     */
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "vehicule_equipement",
            joinColumns = @JoinColumn(name = "id_vehicule"),
            inverseJoinColumns = @JoinColumn(name = "id_equipement"))
    private Set<Equipement> equipements = new HashSet<>();

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
