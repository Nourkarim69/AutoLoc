package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "maintenance")
@Getter
@Setter
@NoArgsConstructor
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMaintenance;

    @Column(nullable = false)
    private LocalDate dateDebut;

    /** Null while the car is still in the garage. */
    private LocalDate dateFin;

    @Column(length = 500)
    private String description;

    /**
     * Owning side (0..* maintenances -> 1 vehicle). EAGER: the car is always shown with the maintenance.
     * No cascade: saving or deleting a maintenance must never touch the car.
     */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_vehicule", nullable = false)
    private Vehicule vehicule;
}
