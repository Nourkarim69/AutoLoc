package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEmploye;

    @Column(nullable = false, length = 50)
    private String nom;

    @Column(nullable = false, length = 50)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleEmploye role;

    /** Login of the employee for the admin area. */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt hash of the password (never the plain password, never sent in JSON). */
    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String motDePasse;
}
