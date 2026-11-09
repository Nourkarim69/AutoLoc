package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.security.AuthInterceptor;
import fr.ece.autoloc.security.LoginAttemptService;
import fr.ece.autoloc.security.SessionUser;
import fr.ece.autoloc.web.dto.LoginRequest;
import fr.ece.autoloc.web.dto.PasswordChangeRequest;
import fr.ece.autoloc.web.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Sign-up, login, logout, "who am I" and password change.
 * Passwords are stored as BCrypt hashes only; repeated failed logins are blocked.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ClientRepository clientRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    /** Used to spend the same time when the email is unknown (does not reveal which emails exist). */
    private final String dummyHash;

    public AuthController(ClientRepository clientRepository, EmployeRepository employeRepository,
                          PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts) {
        this.clientRepository = clientRepository;
        this.employeRepository = employeRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUser register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String permis = request.numPermis().trim().toUpperCase(Locale.ROOT);
        if (clientRepository.existsByEmail(email) || employeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }
        if (clientRepository.existsByNumPermis(permis)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce numéro de permis est déjà enregistré");
        }

        Client client = new Client();
        client.setNom(request.nom().trim());
        client.setPrenom(request.prenom().trim());
        client.setEmail(email);
        client.setTelephone(request.telephone() == null || request.telephone().isBlank()
                ? null : request.telephone().trim());
        client.setNumPermis(permis);
        client.setDateInscription(LocalDate.now());
        client.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        client = clientRepository.save(client);

        return startSession(http, new SessionUser(client.getIdClient(), SessionUser.Role.CLIENT,
                client.getPrenom(), client.getNom(), client.getEmail()));
    }

    @PostMapping("/login")
    public SessionUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String cle = LoginAttemptService.cle(email, http.getRemoteAddr());
        long attente = loginAttempts.minutesRestantes(cle);
        if (attente > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives échouées. Réessayez dans " + attente + " minute(s).");
        }

        Optional<Employe> employe = employeRepository.findByEmail(email);
        if (employe.isPresent()) {
            Employe e = employe.get();
            if (passwordEncoder.matches(request.motDePasse(), e.getMotDePasse())) {
                loginAttempts.succes(cle);
                return startSession(http, new SessionUser(e.getIdEmploye(), SessionUser.Role.ADMIN,
                        e.getPrenom(), e.getNom(), e.getEmail()));
            }
            loginAttempts.echec(cle);
            throw badCredentials();
        }

        Optional<Client> client = clientRepository.findByEmail(email);
        String hash = client.map(Client::getMotDePasse).orElse(dummyHash);
        if (passwordEncoder.matches(request.motDePasse(), hash) && client.isPresent()) {
            loginAttempts.succes(cle);
            Client c = client.get();
            return startSession(http, new SessionUser(c.getIdClient(), SessionUser.Role.CLIENT,
                    c.getPrenom(), c.getNom(), c.getEmail()));
        }
        loginAttempts.echec(cle);
        throw badCredentials();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest http) {
        SessionUser user = AuthInterceptor.currentUser(http);
        return user == null
                ? ResponseEntity.ok(Map.of("connecte", false))
                : ResponseEntity.ok(user);
    }

    /** Logged-in client or employee changes their own password (the current one is required). */
    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest http) {
        SessionUser user = AuthInterceptor.currentUser(http);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vous devez être connecté");
        }
        if (request.ancienMotDePasse().equals(request.nouveauMotDePasse())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent");
        }
        if (user.isAdmin()) {
            Employe e = employeRepository.findById(user.id()).orElseThrow(() -> badCredentials());
            verifier(request.ancienMotDePasse(), e.getMotDePasse());
            e.setMotDePasse(passwordEncoder.encode(request.nouveauMotDePasse()));
        } else {
            Client c = clientRepository.findById(user.id()).orElseThrow(() -> badCredentials());
            verifier(request.ancienMotDePasse(), c.getMotDePasse());
            c.setMotDePasse(passwordEncoder.encode(request.nouveauMotDePasse()));
        }
    }

    private void verifier(String saisi, String hash) {
        if (!passwordEncoder.matches(saisi, hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect");
        }
    }

    private static SessionUser startSession(HttpServletRequest http, SessionUser user) {
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate(); // new session id after login (prevents session fixation)
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(SessionUser.SESSION_KEY, user);
        session.setMaxInactiveInterval(60 * 60); // 1 hour
        return user;
    }

    private static ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
    }
}
