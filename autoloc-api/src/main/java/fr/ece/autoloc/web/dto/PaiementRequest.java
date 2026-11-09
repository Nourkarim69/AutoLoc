package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.ModePaiement;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaiementRequest(
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0.01", message = "Le montant doit être positif")
        @Digits(integer = 8, fraction = 2, message = "2 décimales maximum")
        BigDecimal montant,

        @NotNull(message = "Le mode de paiement est obligatoire")
        ModePaiement modePaiement
) {
}
