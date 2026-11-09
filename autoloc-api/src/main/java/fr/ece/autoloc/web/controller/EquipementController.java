package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.web.dto.EquipementRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Rental options (GPS, child seat...). Reading is public, writing is for admins.
 */
@RestController
@RequestMapping("/api/equipements")
public class EquipementController {

    private final EquipementRepository equipementRepository;

    public EquipementController(EquipementRepository equipementRepository) {
        this.equipementRepository = equipementRepository;
    }

    @GetMapping
    public List<Equipement> findAll() {
        return equipementRepository.findAll(Sort.by("libelle"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Equipement create(@Valid @RequestBody EquipementRequest request) {
        return equipementRepository.save(new Equipement(request.libelle().trim(), request.tarifJournalier()));
    }

    @PutMapping("/{id}")
    public Equipement update(@PathVariable Long id, @Valid @RequestBody EquipementRequest request) {
        Equipement e = get(id);
        e.setLibelle(request.libelle().trim());
        e.setTarifJournalier(request.tarifJournalier());
        return equipementRepository.save(e);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        equipementRepository.delete(get(id));
    }

    private Equipement get(Long id) {
        return equipementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Équipement introuvable"));
    }
}
