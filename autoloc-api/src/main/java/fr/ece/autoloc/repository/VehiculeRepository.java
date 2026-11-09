package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {

    /** Loads the agency in the same SQL query (LEFT JOIN) instead of one query per agency. */
    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "agence")
    List<Vehicule> findAll();

    long countByStatut(StatutVehicule statut);
}
