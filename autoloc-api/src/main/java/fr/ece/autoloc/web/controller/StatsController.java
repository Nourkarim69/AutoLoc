package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.ReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin dashboard figures, computed with COUNT / SUM queries instead of loading whole tables.
 */
@RestController
@RequestMapping("/api/admin")
public class StatsController {

    private final ReservationRepository reservationRepository;
    private final ClientRepository clientRepository;
    private final VehiculeRepository vehiculeRepository;

    public StatsController(ReservationRepository reservationRepository, ClientRepository clientRepository,
                           VehiculeRepository vehiculeRepository) {
        this.reservationRepository = reservationRepository;
        this.clientRepository = clientRepository;
        this.vehiculeRepository = vehiculeRepository;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("reservationsEnCours", reservationRepository.countByStatutIn(ReservationService.ACTIVES));
        s.put("reservationsAConfirmer", reservationRepository.countByStatutIn(EnumSet.of(StatutReservation.EN_ATTENTE)));
        s.put("reservationsTotal", reservationRepository.count());
        s.put("clients", clientRepository.count());
        s.put("vehicules", vehiculeRepository.count());
        s.put("vehiculesEnMaintenance", vehiculeRepository.countByStatut(StatutVehicule.MAINTENANCE));
        s.put("chiffreAffaires", reservationRepository.sumMontantHorsStatut(StatutReservation.ANNULEE));
        return s;
    }
}
