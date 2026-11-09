package fr.ece.autoloc.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    /** A clock we can move forward in the test. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);
    private final String cle = LoginAttemptService.cle("zoe@test.fr", "127.0.0.1");

    @Test
    void bloqueApres5EchecsPendant15Minutes() {
        for (int i = 0; i < LoginAttemptService.MAX_ECHECS - 1; i++) {
            service.echec(cle);
        }
        assertThat(service.minutesRestantes(cle)).isZero();

        service.echec(cle);
        assertThat(service.minutesRestantes(cle)).isBetween(1L, 16L);

        clock.now = clock.now.plus(LoginAttemptService.BLOCAGE).plusSeconds(1);
        assertThat(service.minutesRestantes(cle)).isZero();
    }

    @Test
    void unSuccesRemetLeCompteurAZero() {
        for (int i = 0; i < LoginAttemptService.MAX_ECHECS - 1; i++) {
            service.echec(cle);
        }
        service.succes(cle);
        service.echec(cle);
        assertThat(service.minutesRestantes(cle)).isZero();
    }

    @Test
    void lesEchecsAnciensSontOublies() {
        for (int i = 0; i < LoginAttemptService.MAX_ECHECS - 1; i++) {
            service.echec(cle);
        }
        clock.now = clock.now.plus(Duration.ofMinutes(20));
        service.echec(cle);
        assertThat(service.minutesRestantes(cle)).isZero();
    }

    @Test
    void uneAutreIpNestPasBloquee() {
        for (int i = 0; i < LoginAttemptService.MAX_ECHECS; i++) {
            service.echec(cle);
        }
        assertThat(service.minutesRestantes(LoginAttemptService.cle("zoe@test.fr", "10.0.0.2"))).isZero();
    }
}
