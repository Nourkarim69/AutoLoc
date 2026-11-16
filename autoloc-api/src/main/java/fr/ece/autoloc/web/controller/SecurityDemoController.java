package fr.ece.autoloc.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Three routes that show the Spring Security rules (TP "Secure AutoLoc with Spring Security"):
 * public, any authenticated user (USER or ADMIN) and ADMIN only. See SecurityConfig.filterChain.
 */
@RestController
public class SecurityDemoController {

    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "Public endpoint is reachable";
    }

    @GetMapping("/api/hello")
    public String hello() {
        return "Hello, authenticated user";
    }

    @GetMapping("/api/admin/report")
    public String adminReport() {
        return "Confidential admin report";
    }
}
