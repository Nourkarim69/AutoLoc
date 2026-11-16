package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratRepository extends JpaRepository<Contrat, Long> {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule", "reservation.vehicule.agence"})
    Optional<Contrat> findByReservationIdReservation(Long idReservation);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule", "reservation.vehicule.agence"})
    Optional<Contrat> findById(Long id);

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule", "reservation.vehicule.agence"})
    List<Contrat> findByReservationClientIdClientOrderByDateSignatureDesc(Long idClient);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"reservation", "reservation.client", "reservation.vehicule", "reservation.vehicule.agence"})
    List<Contrat> findAll(Sort sort);
}
