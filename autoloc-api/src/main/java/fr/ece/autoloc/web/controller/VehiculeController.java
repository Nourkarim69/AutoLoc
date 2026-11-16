package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.web.dto.VehiculeRequest;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
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
    private final EquipementRepository equipementRepository;

    public VehiculeController(VehiculeRepository vehiculeRepository, AgenceRepository agenceRepository,
                              EquipementRepository equipementRepository) {
        this.vehiculeRepository = vehiculeRepository;
        this.agenceRepository = agenceRepository;
        this.equipementRepository = equipementRepository;
    }

    @GetMapping
    public List<Vehicule> findAll() {
        return vehiculeRepository.findAll();
    }

    @GetMapping("/{id}")
    public Vehicule findById(@PathVariable Long id) {
        return getOr404(id);
    }

    /** Equipment fitted on the car (many-to-many, join table vehicule_equipement). Public. */
    @GetMapping("/{id}/equipements")
    public List<Equipement> equipements(@PathVariable Long id) {
        getOr404(id);
        return equipementRepository.findByVehicule(id);
    }

    /** Admin: replaces the equipment of the car with the given ids, e.g. [1, 3]. */
    @PutMapping("/{id}/equipements")
    @Transactional
    public List<Equipement> setEquipements(@PathVariable Long id, @RequestBody List<Long> idEquipements) {
        Vehicule vehicule = getOr404(id);
        List<Equipement> found = equipementRepository.findAllById(new HashSet<>(idEquipements));
        if (found.size() != new HashSet<>(idEquipements).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Équipement introuvable");
        }
        vehicule.getEquipements().clear();
        vehicule.getEquipements().addAll(found);
        return equipementRepository.findByVehicule(id);
    }

    // Writes run in one transaction: the car is read with its agency (@EntityGraph) and saved in the same
    // persistence context, so the JSON never meets an unloaded LAZY proxy.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Vehicule create(@Valid @RequestBody VehiculeRequest request) {
        Vehicule vehicule = new Vehicule();
        apply(vehicule, request);
        return vehiculeRepository.save(vehicule);
    }

    @PutMapping("/{id}")
    @Transactional
    public Vehicule update(@PathVariable Long id, @Valid @RequestBody VehiculeRequest request) {
        Vehicule vehicule = getOr404(id);
        apply(vehicule, request);
        return vehiculeRepository.save(vehicule);
    }

    @PatchMapping("/{id}/statut")
    @Transactional
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
