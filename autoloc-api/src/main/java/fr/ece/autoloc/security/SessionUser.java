package fr.ece.autoloc.security;

import java.io.Serializable;

/**
 * The logged-in user, stored in the HTTP session after a successful login.
 *
 * @param id     idClient for a CLIENT, idEmploye for an ADMIN
 * @param role   CLIENT or ADMIN
 * @param prenom first name, shown in the menu
 * @param nom    last name
 * @param email  login
 */
public record SessionUser(Long id, Role role, String prenom, String nom, String email) implements Serializable {

    public static final String SESSION_KEY = "AUTOLOC_USER";

    public enum Role { CLIENT, ADMIN }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
