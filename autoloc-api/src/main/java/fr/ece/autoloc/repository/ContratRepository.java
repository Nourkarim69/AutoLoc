package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratRepository extends JpaRepository<Contrat, Long> {

    Optional<Contrat> findByReservationIdReservation(Long idReservation);

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule"})
    List<Contrat> findByReservationClientIdClientOrderByDateSignatureDesc(Long idClient);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule"})
    List<Contrat> findAll(Sort sort);
}
