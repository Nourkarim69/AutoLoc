package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.web.dto.VehiculeRequest;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * REST API for vehicles (full CRUD + status change). Reading is public, writing is for admins.
 */
@RestController
@RequestMapping("/api/vehicules")
public class VehiculeController {

    private final VehiculeRepository vehiculeRepository;
    private final AgenceRepository agenceRepository;

    public VehiculeController(VehiculeRepository vehiculeRepository, AgenceRepository agenceRepository) {
        this.vehiculeRepository = vehiculeRepository;
        this.agenceRepository = agenceRepository;
    }

    @GetMapping
    public List<Vehicule> findAll() {
        return vehiculeRepository.findAll();
    }

    @GetMapping("/{id}")
    public Vehicule findById(@PathVariable Long id) {
        return getOr404(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Vehicule create(@Valid @RequestBody VehiculeRequest request) {
        Vehicule vehicule = new Vehicule();
        apply(vehicule, request);
        return vehiculeRepository.save(vehicule);
    }

    @PutMapping("/{id}")
    public Vehicule update(@PathVariable Long id, @Valid @RequestBody VehiculeRequest request) {
        Vehicule vehicule = getOr404(id);
        apply(vehicule, request);
        return vehiculeRepository.save(vehicule);
    }

    @PatchMapping("/{id}/statut")
    public Vehicule changeStatut(@PathVariable Long id, @RequestBody Map<String, StatutVehicule> body) {
        StatutVehicule statut = body.get("statut");
        if (statut == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut est obligatoire");
        }
        Vehicule vehicule = getOr404(id);
        vehicule.setStatut(statut);
        return vehiculeRepository.save(vehicule);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        vehiculeRepository.delete(getOr404(id));
    }

    private Vehicule getOr404(Long id) {
        return vehiculeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));
    }

    private void apply(Vehicule vehicule, VehiculeRequest request) {
        vehicule.setImmatriculation(request.immatriculation().trim().toUpperCase());
        vehicule.setMarque(request.marque().trim());
        vehicule.setModele(request.modele().trim());
        vehicule.setCategorie(request.categorie());
        vehicule.setTarifJournalier(request.tarifJournalier());
        vehicule.setStatut(request.statut());
        vehicule.setAgence(request.idAgence() == null ? null : agenceRepository.findById(request.idAgence())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence introuvable")));
    }
}
