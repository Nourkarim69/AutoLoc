package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "paiement")
@Getter
@Setter
@NoArgsConstructor
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPaiement;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private LocalDate datePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModePaiement modePaiement;

    /**
     * Owning side (0..* payments -> 1 contract). LAZY, no cascade (the contract cascades to its payments).
     * Not serialized: the JSON only carries the contract id (see getIdContrat).
     */
    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrat", nullable = false)
    private Contrat contrat;

    /** Id of the contract, read without loading it (Hibernate knows the id of a LAZY proxy). */
    @JsonProperty("idContrat")
    public Long getIdContrat() {
        return contrat == null ? null : contrat.getIdContrat();
    }
}
