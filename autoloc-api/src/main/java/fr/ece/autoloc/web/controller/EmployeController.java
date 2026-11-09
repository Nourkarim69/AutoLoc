package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.security.SessionUser;
import fr.ece.autoloc.web.dto.EmployeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Admin API for employees (who can log in to the admin area). Passwords are BCrypt-hashed.
 */
@RestController
@RequestMapping("/api/employes")
public class EmployeController {

    private final EmployeRepository employeRepository;
    private final AgenceRepository agenceRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeController(EmployeRepository employeRepository, AgenceRepository agenceRepository,
                             ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.employeRepository = employeRepository;
        this.agenceRepository = agenceRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Employe> findAll() {
        return employeRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Employe create(@Valid @RequestBody EmployeRequest request) {
        if (request.motDePasse() == null || request.motDePasse().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe est obligatoire");
        }
        Employe e = new Employe();
        apply(e, request);
        return employeRepository.save(e);
    }

    @PutMapping("/{id}")
    public Employe update(@PathVariable Long id, @Valid @RequestBody EmployeRequest request) {
        Employe e = get(id);
        apply(e, request);
        return employeRepository.save(e);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        if (id.equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas supprimer votre propre compte");
        }
        if (employeRepository.count() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il doit rester au moins un employé");
        }
        employeRepository.delete(get(id));
    }

    private void apply(Employe e, EmployeRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        boolean emailPris = clientRepository.existsByEmail(email)
                || employeRepository.findByEmail(email).filter(o -> !o.getIdEmploye().equals(e.getIdEmploye())).isPresent();
        if (emailPris) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
        }
        e.setNom(request.nom().trim());
        e.setPrenom(request.prenom().trim());
        e.setRole(request.role());
        e.setEmail(email);
        if (request.motDePasse() != null && !request.motDePasse().isBlank()) {
            e.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        }
        e.setAgence(request.idAgence() == null ? null : agenceRepository.findById(request.idAgence())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence introuvable")));
    }

    private Employe get(Long id) {
        return employeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employé introuvable"));
    }
}
