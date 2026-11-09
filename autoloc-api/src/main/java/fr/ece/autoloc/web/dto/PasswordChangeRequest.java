package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String ancienMotDePasse,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Au moins une lettre et un chiffre")
        String nouveauMotDePasse
) {
}
