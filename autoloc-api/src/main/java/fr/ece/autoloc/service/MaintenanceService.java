package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Opening a maintenance puts the car in MAINTENANCE; closing it makes the car available again.
 */
@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehiculeRepository vehiculeRepository;
    private final Clock clock;

    public MaintenanceService(MaintenanceRepository maintenanceRepository, VehiculeRepository vehiculeRepository,
                              Clock clock) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.clock = clock;
    }

    @Transactional
    public Maintenance ouvrir(Long idVehicule, LocalDate dateDebut, String description) {
        Vehicule v = vehiculeRepository.findById(idVehicule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));
        if (maintenanceRepository.existsByVehiculeIdVehiculeAndDateFinIsNull(idVehicule)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce véhicule a déjà une maintenance en cours");
        }
        Maintenance m = new Maintenance();
        m.setVehicule(v);
        m.setDateDebut(dateDebut == null ? LocalDate.now(clock) : dateDebut);
        m.setDescription(description == null || description.isBlank() ? null : description.trim());
        v.setStatut(StatutVehicule.MAINTENANCE);
        return maintenanceRepository.save(m);
    }

    @Transactional
    public Maintenance terminer(Long idMaintenance, LocalDate dateFin) {
        Maintenance m = maintenanceRepository.findById(idMaintenance)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance introuvable"));
        if (m.getDateFin() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette maintenance est déjà terminée");
        }
        LocalDate fin = dateFin == null ? LocalDate.now(clock) : dateFin;
        if (fin.isBefore(m.getDateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de fin doit être après la date de début");
        }
        m.setDateFin(fin);
        m.getVehicule().setStatut(StatutVehicule.DISPONIBLE);
        return m;
    }
}
