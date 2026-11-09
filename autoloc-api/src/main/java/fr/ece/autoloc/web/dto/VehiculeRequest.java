package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Data sent by the client to create or update a vehicle.
 */
public record VehiculeRequest(
        @NotBlank(message = "L'immatriculation est obligatoire")
        @Size(max = 20, message = "20 caractères maximum")
        String immatriculation,

        @NotBlank(message = "La marque est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String marque,

        @NotBlank(message = "Le modèle est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String modele,

        @NotNull(message = "La catégorie est obligatoire")
        CategorieVehicule categorie,

        @NotNull(message = "Le tarif est obligatoire")
        @DecimalMin(value = "0.01", message = "Le tarif doit être positif")
        @Digits(integer = 8, fraction = 2, message = "2 décimales maximum")
        BigDecimal tarifJournalier,

        @NotNull(message = "Le statut est obligatoire")
        StatutVehicule statut,

        /** Pick-up agency (optional). */
        Long idAgence
) {
}
