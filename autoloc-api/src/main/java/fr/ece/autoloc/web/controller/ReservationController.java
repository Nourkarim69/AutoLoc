package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.security.SessionUser;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.web.dto.PageResponse;
import fr.ece.autoloc.web.dto.ReservationRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bookings. Access rules are enforced by AuthInterceptor; ownership by ReservationService.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;

    public ReservationController(ReservationService reservationService, ReservationRepository reservationRepository) {
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
    }

    /** Admin: all reservations, one page at a time (most recent pick-up first). */
    @GetMapping
    public PageResponse<Reservation> findAll(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "dateDebut").and(Sort.by(Sort.Direction.DESC, "idReservation")));
        return PageResponse.of(reservationRepository.findAll(pageable));
    }

    /** Client: my reservations. */
    @GetMapping("/mine")
    public List<Reservation> mine(@SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        requireClient(user);
        return reservationRepository.findByClientIdClientOrderByDateDebutDesc(user.id());
    }

    /** Public: booked periods of a vehicle, so the form can warn before sending. */
    @GetMapping("/indisponibilites")
    public List<Map<String, LocalDate>> indisponibilites(@RequestParam Long vehicule) {
        return reservationService.indisponibilites(vehicule).stream()
                .map(r -> Map.of("dateDebut", r.getDateDebut(), "dateFin", r.getDateFin()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation create(@Valid @RequestBody ReservationRequest request,
                              @SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        requireClient(user);
        return reservationService.reserver(user.id(), request.idVehicule(), request.dateDebut(), request.dateFin(),
                request.idEquipements());
    }

    @PatchMapping("/{id}/prolonger")
    public Reservation prolonger(@PathVariable Long id, @RequestBody Map<String, LocalDate> body,
                                 @SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        LocalDate nouvelleDateFin = body.get("nouvelleDateFin");
        if (nouvelleDateFin == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nouvelle date de retour est obligatoire");
        }
        return reservationService.prolonger(id, nouvelleDateFin, user);
    }

    @PatchMapping("/{id}/annuler")
    public Reservation annuler(@PathVariable Long id, @SessionAttribute(SessionUser.SESSION_KEY) SessionUser user) {
        return reservationService.annuler(id, user);
    }

    /** Admin: confirm (signs the contract), finish or cancel. */
    @PatchMapping("/{id}/statut")
    public Reservation changerStatut(@PathVariable Long id, @RequestBody Map<String, StatutReservation> body) {
        StatutReservation statut = body.get("statut");
        if (statut == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut est obligatoire");
        }
        return reservationService.changerStatut(id, statut);
    }

    private static void requireClient(SessionUser user) {
        if (user.role() != SessionUser.Role.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Connectez-vous avec un compte client pour réserver");
        }
    }
}
