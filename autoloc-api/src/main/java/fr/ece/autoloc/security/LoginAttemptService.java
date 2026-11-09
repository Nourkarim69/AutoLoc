package fr.ece.autoloc.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: after 5 wrong passwords for the same email from the same IP address,
 * login is blocked for 15 minutes. Kept in memory (enough for a single server).
 */
@Component
public class LoginAttemptService {

    static final int MAX_ECHECS = 5;
    static final Duration BLOCAGE = Duration.ofMinutes(15);

    private record Tentatives(int echecs, Instant premierEchec, Instant bloqueJusqua) {
    }

    private final Map<String, Tentatives> tentatives = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Minutes left before a new try is allowed, or 0 if the key is not blocked. */
    public long minutesRestantes(String cle) {
        Tentatives t = tentatives.get(cle);
        Instant now = clock.instant();
        if (t == null || t.bloqueJusqua() == null || !now.isBefore(t.bloqueJusqua())) {
            return 0;
        }
        return Math.max(1, Duration.between(now, t.bloqueJusqua()).toMinutes() + 1);
    }

    public void echec(String cle) {
        Instant now = clock.instant();
        nettoyer(now);
        tentatives.compute(cle, (k, t) -> {
            if (t == null || now.isAfter(t.premierEchec().plus(BLOCAGE))) {
                t = new Tentatives(0, now, null);
            }
            int echecs = t.echecs() + 1;
            return new Tentatives(echecs, t.premierEchec(), echecs >= MAX_ECHECS ? now.plus(BLOCAGE) : null);
        });
    }

    public void succes(String cle) {
        tentatives.remove(cle);
    }

    public static String cle(String email, String ip) {
        return email + "|" + ip;
    }

    /** Avoids unbounded memory growth. */
    private void nettoyer(Instant now) {
        if (tentatives.size() > 10_000) {
            tentatives.entrySet().removeIf(e -> now.isAfter(e.getValue().premierEchec().plus(BLOCAGE.multipliedBy(2))));
        }
    }
}
