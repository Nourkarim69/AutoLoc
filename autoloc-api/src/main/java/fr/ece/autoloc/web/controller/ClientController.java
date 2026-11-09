package fr.ece.autoloc.web.controller;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin API for clients: list and delete. Sign-up goes through /api/auth/register.
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepository clientRepository;

    public ClientController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @GetMapping
    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable"));
        clientRepository.delete(client);
    }
}
