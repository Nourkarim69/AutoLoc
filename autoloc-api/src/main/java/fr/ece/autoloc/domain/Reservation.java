package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "reservation", indexes = {
        // speeds up the "is this car already booked on these dates?" check
        @Index(name = "idx_reservation_dispo", columnList = "id_vehicule, statut, date_debut, date_fin")
})
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idReservation;

    /** Day the client picks up the car. */
    @Column(nullable = false)
    private LocalDate dateDebut;

    /** Day the client returns the car. */
    @Column(nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut;

    /** Number of days x (car daily rate + options daily rates), recomputed when the rental is extended. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    /** How many times the rental has been extended. */
    @Column(nullable = false)
    private int nbProlongations;

    /**
     * 0..* reservations -> 1 client. EAGER: always displayed with the reservation.
     * No cascade: deleting a reservation must never delete the client.
     */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    /** 0..* reservations -> 1 vehicle. EAGER, no cascade (same reasons). */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_vehicule", nullable = false)
    private Vehicule vehicule;

    /**
     * Options chosen for this rental (* reservations <-> * equipments, join table reservation_equipement).
     * Unidirectional, no cascade: the equipment catalogue is shared and must survive reservations.
     * EAGER because it is part of the JSON; @BatchSize loads the options of 50 reservations in one query
     * instead of one query per reservation (avoids N+1).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @JoinTable(name = "reservation_equipement",
            joinColumns = @JoinColumn(name = "id_reservation"),
            inverseJoinColumns = @JoinColumn(name = "id_equipement"))
    private Set<Equipement> equipements = new HashSet<>();
}
