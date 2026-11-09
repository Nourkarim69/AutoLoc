package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByClientIdClientOrderByDateDebutDesc(Long idClient);

    List<Reservation> findAllByOrderByDateDebutDesc();

    List<Reservation> findByVehiculeIdVehiculeAndStatutInAndDateFinAfterOrderByDateDebut(
            Long idVehicule, Collection<StatutReservation> statuts, LocalDate after);

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
