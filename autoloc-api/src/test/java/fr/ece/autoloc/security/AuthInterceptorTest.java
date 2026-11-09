package fr.ece.autoloc.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the access matrix of the REST API: anonymous / client / admin.
 */
class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    private int call(String method, String path, SessionUser.Role role) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (role != null) {
            request.getSession(true).setAttribute(SessionUser.SESSION_KEY,
                    new SessionUser(1L, role, "Test", "User", "t@test.fr"));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());
        return allowed ? 200 : response.getStatus();
    }

    @ParameterizedTest(name = "{0} {1} -> anonyme {2}, client {3}, admin {4}")
    @CsvSource({
            "GET,    /api/vehicules,                     200, 200, 200",
            "POST,   /api/vehicules,                     401, 403, 200",
            "GET,    /api/agences/1,                     200, 200, 200",
            "DELETE, /api/agences/1,                     401, 403, 200",
            "GET,    /api/equipements,                   200, 200, 200",
            "POST,   /api/auth/login,                    200, 200, 200",
            "GET,    /api/reservations/indisponibilites, 200, 200, 200",
            "POST,   /api/reservations,                  401, 200, 200",
            "GET,    /api/reservations/mine,             401, 200, 200",
            "PATCH,  /api/reservations/3/prolonger,      401, 200, 200",
            "GET,    /api/reservations,                  401, 403, 200",
            "PATCH,  /api/reservations/3/statut,         401, 403, 200",
            "GET,    /api/contrats/mine,                 401, 200, 200",
            "GET,    /api/contrats/3/paiements,          401, 200, 200",
            "POST,   /api/contrats/3/paiements,          401, 403, 200",
            "GET,    /api/clients,                       401, 403, 200",
            "GET,    /api/employes,                      401, 403, 200",
            "GET,    /api/admin/stats,                   401, 403, 200",
            "POST,   /api/maintenances,                  401, 403, 200"
    })
    void matriceDesDroits(String method, String path, int anonyme, int client, int admin) throws Exception {
        assertThat(call(method, path, null)).as("anonyme").isEqualTo(anonyme);
        assertThat(call(method, path, SessionUser.Role.CLIENT)).as("client").isEqualTo(client);
        assertThat(call(method, path, SessionUser.Role.ADMIN)).as("admin").isEqualTo(admin);
    }
}
