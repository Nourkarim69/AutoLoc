package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data sent by the client to create or update an agency.
 */
public record AgenceRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "100 caractères maximum")
        String nom,

        @NotBlank(message = "La ville est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String ville,

        @NotBlank(message = "L'adresse est obligatoire")
        @Size(max = 255, message = "255 caractères maximum")
        String adresse,

        @Size(max = 20, message = "20 caractères maximum")
        String telephone
) {
}
