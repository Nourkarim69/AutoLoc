package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.CategorieVehicule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of the booking rules (no database: repositories are mocked, "today" is fixed).
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final SessionUser OWNER = new SessionUser(1L, SessionUser.Role.CLIENT, "Zoé", "Test", "zoe@test.fr");
    private static final SessionUser OTHER = new SessionUser(2L, SessionUser.Role.CLIENT, "Max", "Autre", "max@test.fr");
    private static final SessionUser ADMIN = new SessionUser(1L, SessionUser.Role.ADMIN, "Admin", "AutoLoc", "admin@autoloc.fr");

    @Mock ReservationRepository reservationRepository;
    @Mock VehiculeRepository vehiculeRepository;
    @Mock ClientRepository clientRepository;
    @Mock EquipementRepository equipementRepository;
    @Mock ContratService contratService;

    ReservationService service;
    Client client;
    Vehicule vehicule;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, vehiculeRepository, clientRepository,
                equipementRepository, contratService, CLOCK);
        client = new Client();
        client.setIdClient(1L);
        vehicule = new Vehicule("AB-123-CD", "Peugeot", "208", CategorieVehicule.CITADINE,
                new BigDecimal("40.00"), StatutVehicule.DISPONIBLE, null);
        vehicule.setIdVehicule(10L);
        lenient().when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        lenient().when(vehiculeRepository.findById(10L)).thenReturn(Optional.of(vehicule));
        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Reservation existing(LocalDate debut, LocalDate fin, StatutReservation statut) {
        Reservation r = new Reservation();
        r.setIdReservation(5L);
        r.setClient(client);
        r.setVehicule(vehicule);
        r.setDateDebut(debut);
        r.setDateFin(fin);
        r.setStatut(statut);
        r.setMontantTotal(ReservationService.prix(vehicule, Set.of(), debut, fin));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(r));
        return r;
    }

    private static HttpStatus status(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Nested
    @DisplayName("Réserver")
    class Reserver {

        @Test
        @DisplayName("calcule le prix : jours x (voiture + options) et met la réservation EN_ATTENTE")
        void prixAvecOptions() {
            Equipement gps = new Equipement("GPS", new BigDecimal("5.00"));
            gps.setIdEquipement(1L);
            when(equipementRepository.findAllById(Set.of(1L))).thenReturn(List.of(gps));

            Reservation r = service.reserver(1L, 10L, TODAY.plusDays(1), TODAY.plusDays(4), List.of(1L));

            assertThat(r.getMontantTotal()).isEqualByComparingTo("135.00"); // 3 x (40 + 5)
            assertThat(r.getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
            assertThat(r.getEquipements()).containsExactly(gps);
            assertThat(r.getNbProlongations()).isZero();
        }

        @Test
        @DisplayName("refuse une date de prise dans le passé")
        void datePassee() {
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY.minusDays(1), TODAY.plusDays(2), null))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("refuse un retour avant ou le jour de la prise")
        void retourAvantPrise() {
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY.plusDays(3), TODAY.plusDays(3), null))
                    .hasMessageContaining("après la date de prise");
        }

        @Test
        @DisplayName("refuse une location de plus de 90 jours")
        void tropLongue() {
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY, TODAY.plusDays(ReservationService.MAX_JOURS + 1), null))
                    .hasMessageContaining("90 jours");
        }

        @Test
        @DisplayName("refuse un véhicule en maintenance")
        void maintenance() {
            vehicule.setStatut(StatutVehicule.MAINTENANCE);
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY.plusDays(1), TODAY.plusDays(2), null))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("refuse une double réservation sur des dates qui se chevauchent")
        void chevauchement() {
            when(reservationRepository.existsOverlap(eq(10L), any(), any(), any(), anyLong())).thenReturn(true);
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY.plusDays(1), TODAY.plusDays(2), null))
                    .hasMessageContaining("déjà réservé");
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuse un équipement inconnu")
        void equipementInconnu() {
            when(equipementRepository.findAllById(Set.of(99L))).thenReturn(List.of());
            assertThatThrownBy(() -> service.reserver(1L, 10L, TODAY.plusDays(1), TODAY.plusDays(2), List.of(99L)))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Prolonger")
    class Prolonger {

        @Test
        @DisplayName("repousse le retour, recalcule le prix et met le contrat à jour")
        void ok() {
            Reservation r = existing(TODAY.plusDays(1), TODAY.plusDays(3), StatutReservation.CONFIRMEE);

            Reservation res = service.prolonger(5L, TODAY.plusDays(5), OWNER);

            assertThat(res.getDateFin()).isEqualTo(TODAY.plusDays(5));
            assertThat(res.getMontantTotal()).isEqualByComparingTo("160.00"); // 4 days x 40
            assertThat(res.getNbProlongations()).isEqualTo(1);
            verify(contratService).synchroniserMontant(r);
        }

        @Test
        @DisplayName("refuse une nouvelle date de retour qui n'est pas plus tardive")
        void pasPlusTard() {
            existing(TODAY.plusDays(1), TODAY.plusDays(3), StatutReservation.CONFIRMEE);
            assertThatThrownBy(() -> service.prolonger(5L, TODAY.plusDays(3), OWNER))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("refuse si la voiture est réservée par quelqu'un d'autre ensuite")
        void conflit() {
            existing(TODAY.plusDays(1), TODAY.plusDays(3), StatutReservation.EN_ATTENTE);
            when(reservationRepository.existsOverlap(eq(10L), eq(TODAY.plusDays(3)), eq(TODAY.plusDays(6)), any(), eq(5L)))
                    .thenReturn(true);
            assertThatThrownBy(() -> service.prolonger(5L, TODAY.plusDays(6), OWNER))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("refuse une réservation annulée")
        void annulee() {
            existing(TODAY.plusDays(1), TODAY.plusDays(3), StatutReservation.ANNULEE);
            assertThatThrownBy(() -> service.prolonger(5L, TODAY.plusDays(5), OWNER))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("un autre client ne voit même pas la réservation (404)")
        void autreClient() {
            existing(TODAY.plusDays(1), TODAY.plusDays(3), StatutReservation.CONFIRMEE);
            assertThatThrownBy(() -> service.prolonger(5L, TODAY.plusDays(5), OTHER))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Annuler et changer le statut")
    class Statut {

        @Test
        @DisplayName("le client annule avant le départ et le contrat est invalidé")
        void annulerAvantDepart() {
            Reservation r = existing(TODAY.plusDays(2), TODAY.plusDays(4), StatutReservation.CONFIRMEE);
            assertThat(service.annuler(5L, OWNER).getStatut()).isEqualTo(StatutReservation.ANNULEE);
            verify(contratService).invaliderSiExiste(r);
        }

        @Test
        @DisplayName("le client ne peut plus annuler une location commencée, l'admin si")
        void annulerApresDepart() {
            existing(TODAY, TODAY.plusDays(4), StatutReservation.CONFIRMEE);
            assertThatThrownBy(() -> service.annuler(5L, OWNER))
                    .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.CONFLICT));
            assertThat(service.annuler(5L, ADMIN).getStatut()).isEqualTo(StatutReservation.ANNULEE);
        }

        @Test
        @DisplayName("confirmer crée le contrat")
        void confirmer() {
            Reservation r = existing(TODAY.plusDays(2), TODAY.plusDays(4), StatutReservation.EN_ATTENTE);
            service.changerStatut(5L, StatutReservation.CONFIRMEE);
            verify(contratService).creerSiAbsent(r);
        }
    }

    @Test
    @DisplayName("prix() : 0 option = jours x tarif du véhicule")
    void prixSansOption() {
        assertThat(ReservationService.prix(vehicule, Set.of(), TODAY, TODAY.plusDays(7)))
                .isEqualByComparingTo("280.00");
    }
}
