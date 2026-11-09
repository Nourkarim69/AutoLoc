package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    List<Paiement> findByContratIdContratOrderByDatePaiementAsc(Long idContrat);

    @Query("select coalesce(sum(p.montant), 0) from Paiement p where p.contrat.idContrat = :idContrat")
    BigDecimal totalPaye(@Param("idContrat") Long idContrat);

    /** Total paid for several contracts in ONE query: rows of [idContrat, sum]. */
    @Query("select p.contrat.idContrat, sum(p.montant) from Paiement p "
            + "where p.contrat.idContrat in :ids group by p.contrat.idContrat")
    List<Object[]> totauxPayes(@Param("ids") Collection<Long> ids);
}
