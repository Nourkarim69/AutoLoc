package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MaintenanceRequest(
        @NotNull(message = "Choisissez un véhicule")
        Long idVehicule,

        /** Defaults to today. */
        LocalDate dateDebut,

        @Size(max = 500, message = "500 caractères maximum")
        String description
) {
}
