package fr.ece.autoloc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Access rules for the REST API:
 * <ul>
 *     <li>public: /api/auth/**, and reading vehicles, agencies, equipment and booked dates</li>
 *     <li>logged-in user (client or admin): /api/reservations/**, /api/contrats/mine,
 *         GET /api/contrats/{id}/paiements</li>
 *     <li>admin only: everything else</li>
 * </ul>
 * Fine-grained checks (a client can only see their own data) are done in the controllers and services.
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();

        if ("OPTIONS".equals(method) || isPublic(path, method)) {
            return true;
        }

        SessionUser user = currentUser(request);
        if (user == null) {
            return deny(response, HttpServletResponse.SC_UNAUTHORIZED, "Vous devez être connecté");
        }
        if (user.isAdmin() || isForAnyLoggedUser(path, method)) {
            return true;
        }
        return deny(response, HttpServletResponse.SC_FORBIDDEN, "Accès réservé à l'administration");
    }

    static boolean isPublic(String path, String method) {
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        boolean read = "GET".equals(method);
        return read && (path.startsWith("/api/vehicules")
                || path.startsWith("/api/agences")
                || path.startsWith("/api/equipements")
                || path.equals("/api/reservations/indisponibilites"));
    }

    static boolean isForAnyLoggedUser(String path, String method) {
        if (path.startsWith("/api/reservations")) {
            // listing every reservation and changing a status are admin actions
            boolean adminOnly = ("GET".equals(method) && path.equals("/api/reservations"))
                    || ("PATCH".equals(method) && path.matches("/api/reservations/\\d+/statut"));
            return !adminOnly;
        }
        return "GET".equals(method)
                && (path.equals("/api/contrats/mine") || path.matches("/api/contrats/\\d+/paiements"));
    }

    public static SessionUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
    }

    private static boolean deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
        return false;
    }
}
