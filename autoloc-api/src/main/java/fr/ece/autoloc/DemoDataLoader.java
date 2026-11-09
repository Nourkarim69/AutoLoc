package fr.ece.autoloc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Inserts demo data at startup (dev profile only, and only into empty tables):
 * vehicles, agencies and one admin account (password stored as a BCrypt hash).
 */
@Component
@Profile("dev")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DemoDataLoader(VehiculeRepository vehiculeRepository, AgenceRepository agenceRepository,
                          EmployeRepository employeRepository, PasswordEncoder passwordEncoder,
                          @Value("${autoloc.admin.email}") String adminEmail,
                          @Value("${autoloc.admin.password}") String adminPassword) {
        this.vehiculeRepository = vehiculeRepository;
        this.agenceRepository = agenceRepository;
        this.employeRepository = employeRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (vehiculeRepository.count() == 0) {
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

        if (agenceRepository.count() == 0) {
            agenceRepository.saveAll(List.of(
                    new Agence(null, "AutoLoc Paris Centre", "Paris", "37 Quai de Grenelle, 75015 Paris", "01 44 39 06 00"),
                    new Agence(null, "AutoLoc Lyon Part-Dieu", "Lyon", "17 Boulevard Vivier-Merle, 69003 Lyon", "04 72 00 00 00"),
                    new Agence(null, "AutoLoc Marseille Saint-Charles", "Marseille", "Square Narvik, 13001 Marseille", "04 91 00 00 00")
            ));
            log.info("Demo data loaded: {} agencies", agenceRepository.count());
        }

        if (employeRepository.findByEmail(adminEmail).isEmpty()) {
            Employe admin = new Employe(null, "Admin", "AutoLoc", RoleEmploye.MANAGER, adminEmail,
                    passwordEncoder.encode(adminPassword));
            employeRepository.save(admin);
            log.info("Admin account created: {} (password from autoloc.admin.password)", adminEmail);
        }
    }
}
