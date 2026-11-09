package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.web.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Admin API for clients: paginated list, delete, password reset. Sign-up goes through /api/auth/register.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientController(ClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public PageResponse<Client> findAll(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "idClient"));
        return PageResponse.of(clientRepository.findAll(pageable));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        clientRepository.delete(get(id));
    }

    /**
     * "Forgotten password" handled by the agency: generates a temporary password, stores only its BCrypt hash
     * and returns it ONCE so the employee can give it to the client, who then changes it in their space.
     */
    @PostMapping("/{id}/reinitialiser-mot-de-passe")
    @Transactional
    public Map<String, String> reinitialiserMotDePasse(@PathVariable Long id) {
        Client client = get(id);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        String temporaire = sb.append(RANDOM.nextInt(10)).toString();
        client.setMotDePasse(passwordEncoder.encode(temporaire));
        return Map.of("motDePasseTemporaire", temporaire);
    }

    private Client get(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable"));
    }
}
