package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rental contract, signed when the agency confirms a reservation.
 */
@Entity
@Table(name = "contrat")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
public class Contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idContrat;

    @Column(nullable = false)
    private LocalDate dateSignature;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    @Column(nullable = false)
    private boolean valide;

    /**
     * 1 reservation <-> 0..1 contract. Owning side here (unique foreign key id_reservation).
     * LAZY: the contract lists load the reservation with @EntityGraph. The cascade is on the Reservation side.
     */
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reservation", nullable = false, unique = true)
    private Reservation reservation;

    /**
     * Payments of this contract (1 contract -> 0..* payments).
     * cascade ALL + orphanRemoval: payments belong to the contract (composition), they are deleted with it.
     * LAZY + JsonIgnore: the total paid is computed by a query instead of loading the list.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "contrat", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Paiement> paiements = new ArrayList<>();
}
