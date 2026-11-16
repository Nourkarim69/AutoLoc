package fr.ece.autoloc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bridge between the website login (HTTP session, see AuthController) and Spring Security.
 * When the request carries a logged-in website session and no HTTP Basic credentials, the session user
 * becomes the Spring Security principal for this request: a client gets ROLE_USER, an employee ROLE_ADMIN.
 * Nothing is stored: the context only lives for the current request.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(SessionUser.SESSION_KEY);
        if (attribute instanceof SessionUser user && SecurityContextHolder.getContext().getAuthentication() == null) {
            String role = user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.email(), null, List.of(new SimpleGrantedAuthority(role)));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
