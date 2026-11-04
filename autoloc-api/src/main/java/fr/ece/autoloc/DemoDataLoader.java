package fr.ece.autoloc;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inserts a few demo vehicles at startup (dev profile only, and only if the table is empty).
 */
@Component
@Profile("dev")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final VehiculeRepository vehiculeRepository;

    public DemoDataLoader(VehiculeRepository vehiculeRepository) {
        this.vehiculeRepository = vehiculeRepository;
    }

    @Override
    public void run(String... args) {
        if (vehiculeRepository.count() > 0) {
            return;
        }
        vehiculeRepository.saveAll(List.of(
                new Vehicule(null, "AB-123-CD", "Peugeot", "208", CategorieVehicule.CITADINE,
                        new BigDecimal("39.90"), StatutVehicule.DISPONIBLE),
                new Vehicule(null, "EF-456-GH", "Renault", "Talisman", CategorieVehicule.BERLINE,
                        new BigDecimal("64.50"), StatutVehicule.DISPONIBLE),
                new Vehicule(null, "IJ-789-KL", "Dacia", "Duster", CategorieVehicule.SUV,
                        new BigDecimal("55.00"), StatutVehicule.MAINTENANCE)
        ));
        log.info("Demo data loaded: {} vehicles", vehiculeRepository.count());
    }
}
