package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business rules for bookings: dates, availability (no double booking), price, extension, cancellation.
 */
@Service
public class ReservationService {

    /** Reservations that block the car. */
    public static final Set<StatutReservation> ACTIVES = EnumSet.of(StatutReservation.EN_ATTENTE, StatutReservation.CONFIRMEE);

    static final int MAX_JOURS = 90;

    private final ReservationRepository reservationRepository;
    private final VehiculeRepository vehiculeRepository;
    private final ClientRepository clientRepository;
    private final EquipementRepository equipementRepository;
    private final ContratService contratService;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
                              VehiculeRepository vehiculeRepository,
                              ClientRepository clientRepository,
                              EquipementRepository equipementRepository,
                              ContratService contratService,
                              Clock clock) {
        this.reservationRepository = reservationRepository;
        this.vehiculeRepository = vehiculeRepository;
        this.clientRepository = clientRepository;
        this.equipementRepository = equipementRepository;
        this.contratService = contratService;
        this.clock = clock;
    }

    @Transactional
    public Reservation reserver(Long idClient, Long idVehicule, LocalDate debut, LocalDate fin,
                               Collection<Long> idEquipements) {
        Client client = clientRepository.findById(idClient)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte introuvable, reconnectez-vous"));
        Vehicule vehicule = vehiculeRepository.findById(idVehicule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));

        if (debut.isBefore(LocalDate.now(clock))) {
            throw bad("La date de prise ne peut pas être dans le passé");
        }
        verifierDuree(debut, fin);
        if (vehicule.getStatut() == StatutVehicule.MAINTENANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce véhicule est en maintenance");
        }
        if (reservationRepository.existsOverlap(idVehicule, debut, fin, ACTIVES, -1L)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce véhicule est déjà réservé sur ces dates");
        }

        Set<Equipement> equipements = chargerEquipements(idEquipements);

        Reservation r = new Reservation();
        r.setClient(client);
        r.setVehicule(vehicule);
        r.setDateDebut(debut);
        r.setDateFin(fin);
        r.setStatut(StatutReservation.EN_ATTENTE);
        r.setNbProlongations(0);
        r.setEquipements(equipements);
        r.setMontantTotal(prix(vehicule, equipements, debut, fin));
        return reservationRepository.save(r);
    }

    /** Extends the rental: the new return day must be later and the car free in between. */
    @Transactional
    public Reservation prolonger(Long idReservation, LocalDate nouvelleDateFin, SessionUser user) {
        Reservation r = getAutorisee(idReservation, user);
        if (!ACTIVES.contains(r.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une réservation en cours peut être prolongée");
        }
        if (!nouvelleDateFin.isAfter(r.getDateFin())) {
            throw bad("La nouvelle date de retour doit être après le " + r.getDateFin());
        }
        verifierDuree(r.getDateDebut(), nouvelleDateFin);
        if (reservationRepository.existsOverlap(r.getVehicule().getIdVehicule(), r.getDateFin(), nouvelleDateFin,
                ACTIVES, r.getIdReservation())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Prolongation impossible : le véhicule est déjà réservé sur cette période");
        }
        r.setDateFin(nouvelleDateFin);
        r.setNbProlongations(r.getNbProlongations() + 1);
        r.setMontantTotal(prix(r.getVehicule(), r.getEquipements(), r.getDateDebut(), nouvelleDateFin));
        contratService.synchroniserMontant(r);
        return reservationRepository.save(r);
    }

    @Transactional
    public Reservation annuler(Long idReservation, SessionUser user) {
        Reservation r = getAutorisee(idReservation, user);
        if (!ACTIVES.contains(r.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette réservation ne peut plus être annulée");
        }
        if (!user.isAdmin() && !r.getDateDebut().isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La location a déjà commencé : contactez votre agence pour l'annuler");
        }
        r.setStatut(StatutReservation.ANNULEE);
        contratService.invaliderSiExiste(r);
        return reservationRepository.save(r);
    }

    /** Admin: confirming signs the contract, cancelling invalidates it. */
    @Transactional
    public Reservation changerStatut(Long idReservation, StatutReservation statut) {
        Reservation r = reservationRepository.findById(idReservation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable"));
        if (ACTIVES.contains(statut) && !ACTIVES.contains(r.getStatut())
                && reservationRepository.existsOverlap(r.getVehicule().getIdVehicule(), r.getDateDebut(),
                r.getDateFin(), ACTIVES, r.getIdReservation())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le véhicule a été réservé entre-temps sur ces dates");
        }
        r.setStatut(statut);
        if (statut == StatutReservation.CONFIRMEE) {
            contratService.creerSiAbsent(r);
        } else if (statut == StatutReservation.ANNULEE) {
            contratService.invaliderSiExiste(r);
        }
        return reservationRepository.save(r);
    }

    public List<Reservation> indisponibilites(Long idVehicule) {
        return reservationRepository.findByVehiculeIdVehiculeAndStatutInAndDateFinAfterOrderByDateDebut(
                idVehicule, ACTIVES, LocalDate.now(clock).minusDays(1));
    }

    /** Price = number of days x (car daily rate + daily rate of each option). */
    public static BigDecimal prix(Vehicule v, Collection<Equipement> equipements, LocalDate debut, LocalDate fin) {
        long jours = ChronoUnit.DAYS.between(debut, fin);
        BigDecimal parJour = v.getTarifJournalier();
        for (Equipement e : equipements) {
            parJour = parJour.add(e.getTarifJournalier());
        }
        return parJour.multiply(BigDecimal.valueOf(jours));
    }

    private Set<Equipement> chargerEquipements(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> uniques = new HashSet<>(ids);
        List<Equipement> found = equipementRepository.findAllById(uniques);
        if (found.size() != uniques.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Équipement introuvable");
        }
        return new HashSet<>(found);
    }

    private Reservation getAutorisee(Long idReservation, SessionUser user) {
        Reservation r = reservationRepository.findById(idReservation)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable"));
        if (!user.isAdmin() && !r.getClient().getIdClient().equals(user.id())) {
            // Same answer as "not found": a client cannot learn that other reservations exist.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable");
        }
        return r;
    }

    private static void verifierDuree(LocalDate debut, LocalDate fin) {
        if (!fin.isAfter(debut)) {
            throw bad("La date de retour doit être après la date de prise");
        }
        if (ChronoUnit.DAYS.between(debut, fin) > MAX_JOURS) {
            throw bad("Une location ne peut pas dépasser " + MAX_JOURS + " jours");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
