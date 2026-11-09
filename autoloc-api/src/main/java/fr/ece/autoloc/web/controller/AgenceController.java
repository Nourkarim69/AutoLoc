package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.web.dto.AgenceRequest;
import fr.ece.autoloc.repository.AgenceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST API for agencies (full CRUD).
 */
@RestController
@RequestMapping("/api/agences")
public class AgenceController {

    private final AgenceRepository agenceRepository;

    public AgenceController(AgenceRepository agenceRepository) {
        this.agenceRepository = agenceRepository;
    }

    @GetMapping
    public List<Agence> findAll() {
        return agenceRepository.findAll();
    }

    @GetMapping("/{id}")
    public Agence findById(@PathVariable Long id) {
        return getOr404(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Agence create(@Valid @RequestBody AgenceRequest request) {
        Agence agence = new Agence();
        apply(agence, request);
        return agenceRepository.save(agence);
    }

    @PutMapping("/{id}")
    public Agence update(@PathVariable Long id, @Valid @RequestBody AgenceRequest request) {
        Agence agence = getOr404(id);
        apply(agence, request);
        return agenceRepository.save(agence);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        agenceRepository.delete(getOr404(id));
    }

    private Agence getOr404(Long id) {
        return agenceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence introuvable"));
    }

    private static void apply(Agence agence, AgenceRequest request) {
        agence.setNom(request.nom().trim());
        agence.setVille(request.ville().trim());
        agence.setAdresse(request.adresse().trim());
        agence.setTelephone(request.telephone() == null || request.telephone().isBlank()
                ? null : request.telephone().trim());
    }
}
