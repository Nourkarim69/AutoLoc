package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.security.SessionUser;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.ContratService.ContratView;
import fr.ece.autoloc.web.dto.PaiementRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Contracts (created automatically when a reservation is confirmed) and their payments.
 */
@RestController
@RequestMapping("/api/contrats")
public class ContratController {

    private final ContratRepository contratRepository;
    private final PaiementRepository paiementRepository;
    private final ContratService contratService;

    public ContratController(ContratRepository contratRepository, PaiementRepository paiementRepository,
                             ContratService contratService) {
        this.contratRepository = contratRepository;
        this.paiementRepository = paiementRepository;
        this.contratService = contratService;
    }

    /** Admin: every contract with paid / remaining amounts. */
    @GetMapping
    public List<ContratView> findAll() {
        return contratService.avecPaiements(contratRepository.findAll(Sort.by(Sort.Direction.DESC, "idContrat")));
    }

    /** Client: my contracts. */
    @GetMapping("/mine")
    public List<ContratView> mine(@SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        if (user.isAdmin()) {
            return List.of();
        }
        return contratService.avecPaiements(
                contratRepository.findByReservationClientIdClientOrderByDateSignatureDesc(user.id()));
    }

    /** Payments of a contract: the admin, or the client who owns it. */
    @GetMapping("/{id}/paiements")
    public List<Paiement> paiements(@PathVariable Long id, @SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        Contrat c = contratService.get(id);
        if (!user.isAdmin() && !c.getReservation().getClient().getIdClient().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable");
        }
        return paiementRepository.findByContratIdContratOrderByDatePaiementAsc(id);
    }

    /** Admin: record a payment received at the agency. */
    @PostMapping("/{id}/paiements")
    @ResponseStatus(HttpStatus.CREATED)
    public Paiement payer(@PathVariable Long id, @Valid @RequestBody PaiementRequest request) {
        return contratService.payer(id, request.montant(), request.modePaiement());
    }

    @PatchMapping("/{id}/invalider")
    public Contrat invalider(@PathVariable Long id) {
        return contratService.invalider(id);
    }
}
