package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contracts (one per confirmed reservation) and their payments.
 */
@Service
public class ContratService {

    private final ContratRepository contratRepository;
    private final PaiementRepository paiementRepository;
    private final Clock clock;

    public ContratService(ContratRepository contratRepository, PaiementRepository paiementRepository, Clock clock) {
        this.contratRepository = contratRepository;
        this.paiementRepository = paiementRepository;
        this.clock = clock;
    }

    /** A contract with what has been paid so far. */
    public record ContratView(Contrat contrat, BigDecimal totalPaye, BigDecimal resteAPayer) {
    }

    @Transactional
    public void creerSiAbsent(Reservation r) {
        Optional<Contrat> existant = contratRepository.findByReservationIdReservation(r.getIdReservation());
        if (existant.isPresent()) {
            existant.get().setValide(true);
            existant.get().setMontantTotal(r.getMontantTotal());
            return;
        }
        Contrat c = new Contrat();
        c.setReservation(r);
        c.setDateSignature(LocalDate.now(clock));
        c.setMontantTotal(r.getMontantTotal());
        c.setValide(true);
        contratRepository.save(c);
    }

    @Transactional
    public void invaliderSiExiste(Reservation r) {
        contratRepository.findByReservationIdReservation(r.getIdReservation()).ifPresent(c -> c.setValide(false));
    }

    /** Keeps the contract amount equal to the reservation amount after an extension. */
    @Transactional
    public void synchroniserMontant(Reservation r) {
        contratRepository.findByReservationIdReservation(r.getIdReservation())
                .ifPresent(c -> c.setMontantTotal(r.getMontantTotal()));
    }

    @Transactional
    public Paiement payer(Long idContrat, BigDecimal montant, ModePaiement mode) {
        Contrat c = get(idContrat);
        if (!c.isValide()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce contrat n'est plus valide");
        }
        BigDecimal reste = c.getMontantTotal().subtract(paiementRepository.totalPaye(idContrat));
        if (montant.compareTo(reste) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant dépasse le reste à payer (" + reste + " €)");
        }
        Paiement p = new Paiement();
        p.setContrat(c);
        p.setMontant(montant);
        p.setModePaiement(mode);
        p.setDatePaiement(LocalDate.now(clock));
        return paiementRepository.save(p);
    }

    @Transactional
    public Contrat invalider(Long idContrat) {
        Contrat c = get(idContrat);
        c.setValide(false);
        return c;
    }

    public Contrat get(Long idContrat) {
        return contratRepository.findById(idContrat)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable"));
    }

    /** Adds the paid amounts with a single GROUP BY query (no query per contract). */
    public List<ContratView> avecPaiements(Collection<Contrat> contrats) {
        Map<Long, BigDecimal> payes = new HashMap<>();
        if (!contrats.isEmpty()) {
            for (Object[] row : paiementRepository.totauxPayes(contrats.stream().map(Contrat::getIdContrat).toList())) {
                payes.put((Long) row[0], (BigDecimal) row[1]);
            }
        }
        return contrats.stream().map(c -> {
            BigDecimal paye = payes.getOrDefault(c.getIdContrat(), BigDecimal.ZERO);
            return new ContratView(c, paye, c.getMontantTotal().subtract(paye).max(BigDecimal.ZERO));
        }).toList();
    }
}
