package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Booking form: the car chosen in the drop-down list, the pick-up day and the return day.
 */
public record ReservationRequest(
        @NotNull(message = "Choisissez un véhicule")
        Long idVehicule,

        @NotNull(message = "La date de prise est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de retour est obligatoire")
        LocalDate dateFin
) {
}
