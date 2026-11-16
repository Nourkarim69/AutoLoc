package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Employe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    Optional<Employe> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "agence")
    Optional<Employe> findById(Long id);

    @Override
    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = "agence")
    List<Employe> findAll();
}
