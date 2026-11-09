package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EquipementRequest(
        @NotBlank(message = "Le libellé est obligatoire")
        @Size(max = 100, message = "100 caractères maximum")
        String libelle,

        @NotNull(message = "Le tarif est obligatoire")
        @DecimalMin(value = "0.00", message = "Le tarif ne peut pas être négatif")
        @Digits(integer = 8, fraction = 2, message = "2 décimales maximum")
        BigDecimal tarifJournalier
) {
}
