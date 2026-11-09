package fr.ece.autoloc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Inserts demo data at startup (dev profile only, and only into empty tables):
 * agencies, vehicles, equipment and one admin account (password stored as a BCrypt hash).
 */
@Component
@Profile("dev")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;
    private final EmployeRepository employeRepository;
    private final EquipementRepository equipementRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DemoDataLoader(VehiculeRepository vehiculeRepository, AgenceRepository agenceRepository,
                          EmployeRepository employeRepository, EquipementRepository equipementRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${autoloc.admin.email}") String adminEmail,
                          @Value("${autoloc.admin.password}") String adminPassword) {
        this.vehiculeRepository = vehiculeRepository;
        this.agenceRepository = agenceRepository;
        this.employeRepository = employeRepository;
        this.equipementRepository = equipementRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (agenceRepository.count() == 0) {
            agenceRepository.saveAll(List.of(
                    new Agence("AutoLoc Paris Centre", "Paris", "37 Quai de Grenelle, 75015 Paris", "01 44 39 06 00"),
                    new Agence("AutoLoc Lyon Part-Dieu", "Lyon", "17 Boulevard Vivier-Merle, 69003 Lyon", "04 72 00 00 00"),
                    new Agence("AutoLoc Marseille Saint-Charles", "Marseille", "Square Narvik, 13001 Marseille", "04 91 00 00 00")
            ));
            log.info("Demo data loaded: {} agencies", agenceRepository.count());
        }
        List<Agence> agences = agenceRepository.findAll();

        if (vehiculeRepository.count() == 0) {
            vehiculeRepository.saveAll(List.of(
                    new Vehicule("AB-123-CD", "Peugeot", "208", CategorieVehicule.CITADINE,
                            new BigDecimal("39.90"), StatutVehicule.DISPONIBLE, agences.get(0)),
                    new Vehicule("EF-456-GH", "Renault", "Talisman", CategorieVehicule.BERLINE,
                            new BigDecimal("64.50"), StatutVehicule.DISPONIBLE, agences.get(1 % agences.size())),
                    new Vehicule("IJ-789-KL", "Dacia", "Duster", CategorieVehicule.SUV,
                            new BigDecimal("55.00"), StatutVehicule.MAINTENANCE, agences.get(2 % agences.size()))
            ));
            log.info("Demo data loaded: {} vehicles", vehiculeRepository.count());
        } else {
            // cars created before the Vehicule -> Agence association: attach them to an agency
            List<Vehicule> sansAgence = vehiculeRepository.findAll().stream().filter(v -> v.getAgence() == null).toList();
            for (int i = 0; i < sansAgence.size() && !agences.isEmpty(); i++) {
                sansAgence.get(i).setAgence(agences.get(i % agences.size()));
            }
        }

        if (equipementRepository.count() == 0) {
            equipementRepository.saveAll(List.of(
                    new Equipement("GPS", new BigDecimal("5.00")),
                    new Equipement("Siège bébé", new BigDecimal("4.00")),
                    new Equipement("Chaînes neige", new BigDecimal("6.00")),
                    new Equipement("Conducteur supplémentaire", new BigDecimal("8.00"))
            ));
            log.info("Demo data loaded: {} equipments", equipementRepository.count());
        }

        if (employeRepository.findByEmail(adminEmail).isEmpty()) {
            employeRepository.save(new Employe("AutoLoc", "Admin", RoleEmploye.MANAGER, adminEmail,
                    passwordEncoder.encode(adminPassword), agences.isEmpty() ? null : agences.get(0)));
            log.info("Admin account created: {} (password from autoloc.admin.password)", adminEmail);
        }
    }
}
