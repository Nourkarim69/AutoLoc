package fr.ece.autoloc.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "client")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idClient;

    @Column(nullable = false, length = 50)
    private String nom;

    @Column(nullable = false, length = 50)
    private String prenom;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String telephone;

    @Column(nullable = false, unique = true, length = 30)
    private String numPermis;

    @Column(nullable = false)
    private LocalDate dateInscription;

    /** BCrypt hash of the password (never the plain password, never sent in JSON). */
    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String motDePasse;

    /**
     * Inverse side of Reservation.client (1 client -> 0..* reservations).
     * LAZY + JsonIgnore: a client's reservations are read with GET /api/reservations/mine.
     * cascade PERSIST: a new reservation added to a new client is saved with it.
     * No REMOVE: reservations are a business history and must not disappear with the client.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<Reservation> reservations = new ArrayList<>();
}
