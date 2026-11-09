package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.service.MaintenanceService;
import fr.ece.autoloc.web.dto.MaintenanceRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin API for maintenance: opening one puts the car in MAINTENANCE, finishing it makes it available again.
 */
@RestController
@RequestMapping("/api/maintenances")
public class MaintenanceController {

    private final MaintenanceRepository maintenanceRepository;
    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceRepository maintenanceRepository, MaintenanceService maintenanceService) {
        this.maintenanceRepository = maintenanceRepository;
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public List<Maintenance> findAll() {
        return maintenanceRepository.findAll(Sort.by(Sort.Direction.DESC, "dateDebut", "idMaintenance"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Maintenance ouvrir(@Valid @RequestBody MaintenanceRequest request) {
        return maintenanceService.ouvrir(request.idVehicule(), request.dateDebut(), request.description());
    }

    @PatchMapping("/{id}/terminer")
    public Maintenance terminer(@PathVariable Long id, @RequestBody(required = false) Map<String, LocalDate> body) {
        return maintenanceService.terminer(id, body == null ? null : body.get("dateFin"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance introuvable"));
        if (m.getDateFin() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Terminez la maintenance avant de la supprimer");
        }
        maintenanceRepository.delete(m);
    }
}
