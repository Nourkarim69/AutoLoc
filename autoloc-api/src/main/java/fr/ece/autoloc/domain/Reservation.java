package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    /** Number of days x daily rate, recomputed when the rental is extended. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    /** How many times the rental has been extended. */
    @Column(nullable = false)
    private int nbProlongations;

    /** A client makes 0..* reservations. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_client", nullable = false)
    private Client client;

    /** A vehicle is reserved 0..* times. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_vehicule", nullable = false)
    private Vehicule vehicule;
}
