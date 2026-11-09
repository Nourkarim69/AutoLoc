package fr.ece.autoloc.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data sent by the sign-up form to create a client account.
 */
public record RegisterRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String prenom,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Email invalide")
        @Size(max = 100, message = "100 caractères maximum")
        String email,

        @Size(max = 20, message = "20 caractères maximum")
        String telephone,

        @NotBlank(message = "Le numéro de permis est obligatoire")
        @Size(max = 30, message = "30 caractères maximum")
        String numPermis,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Au moins une lettre et un chiffre")
        String motDePasse
) {
}
