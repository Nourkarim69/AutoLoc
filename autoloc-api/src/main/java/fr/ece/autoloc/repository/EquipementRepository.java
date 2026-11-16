package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Equipement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EquipementRepository extends JpaRepository<Equipement, Long> {

    /** Equipment fitted on one car (join table vehicule_equipement), sorted by label. */
    @Query("select e from Vehicule v join v.equipements e where v.idVehicule = :idVehicule order by e.libelle")
    List<Equipement> findByVehicule(@Param("idVehicule") Long idVehicule);
}
