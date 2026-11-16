package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** One query with JOINs for client, vehicle and agency (no N+1); options are batch-loaded. */
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"client", "vehicule", "vehicule.agence", "contrat"})
    List<Reservation> findByClientIdClientOrderByDateDebutDesc(Long idClient);

    /** Admin list, one page at a time. */
    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"client", "vehicule", "vehicule.agence", "contrat"})
    Page<Reservation> findAll(Pageable pageable);

    /** One reservation with everything the JSON and the business rules need (associations are LAZY). */
    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"client", "vehicule", "vehicule.agence", "contrat"})
    Optional<Reservation> findById(Long id);

    List<Reservation> findByVehiculeIdVehiculeAndStatutInAndDateFinAfterOrderByDateDebut(
            Long idVehicule, Collection<StatutReservation> statuts, LocalDate after);

    long countByStatutIn(Collection<StatutReservation> statuts);

    @Query("select coalesce(sum(r.montantTotal), 0) from Reservation r where r.statut <> :exclu")
    BigDecimal sumMontantHorsStatut(@Param("exclu") StatutReservation exclu);

    /**
     * True if the vehicle already has an active reservation overlapping [debut, fin[.
     * The return day is free again, so back-to-back rentals are allowed.
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicule.idVehicule = :idVehicule
              and r.statut in :statuts
              and r.idReservation <> :exclude
              and r.dateDebut < :fin
              and r.dateFin > :debut
            """)
    boolean existsOverlap(@Param("idVehicule") Long idVehicule,
                          @Param("debut") LocalDate debut,
                          @Param("fin") LocalDate fin,
                          @Param("statuts") Collection<StatutReservation> statuts,
                          @Param("exclude") Long exclude);
}
