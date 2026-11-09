package fr.ece.autoloc.web.dto;

import fr.ece.autoloc.domain.RoleEmploye;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update an employee. The password is required on creation, optional on update (empty = unchanged).
 */
public record EmployeRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String nom,

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 50, message = "50 caractères maximum")
        String prenom,

        @NotNull(message = "Le rôle est obligatoire")
        RoleEmploye role,

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Email invalide")
        @Size(max = 100, message = "100 caractères maximum")
        String email,

        @Size(max = 72, message = "72 caractères maximum")
        @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "8 caractères min., avec lettres et chiffres")
        String motDePasse,

        Long idAgence
) {
}
