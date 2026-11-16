package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Maintenance;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"vehicule", "vehicule.agence"})
    List<Maintenance> findAll(Sort sort);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {"vehicule", "vehicule.agence"})
    Optional<Maintenance> findById(Long id);

    boolean existsByVehiculeIdVehiculeAndDateFinIsNull(Long idVehicule);
}
