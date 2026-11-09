package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNumPermis(String numPermis);
}
