package fr.ece.autoloc.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Security of AutoLoc, in two layers:
 * <ol>
 *   <li>Spring Security ({@link #filterChain}): HTTP Basic with two in-memory demo accounts, role rules on
 *       /api/public, /api/hello, /api/admin and on the Actuator endpoints. The website session is bridged
 *       into Spring Security by {@link SessionAuthenticationFilter} (client = USER, employee = ADMIN).</li>
 *   <li>{@link AuthInterceptor}: the detailed rules of the business API (client / admin per route).</li>
 * </ol>
 * Also: BCrypt for passwords and browser caching of the CSS / JS files.
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    /** Routes fully handled by Spring Security (demo of the TP); the interceptor skips them. */
    private static final String[] SPRING_SECURITY_ROUTES = {"/api/public/**", "/api/hello", "/api/admin/report"};

    /** BCrypt with cost 12: each hash is salted and slow to brute-force. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Current time, injectable so that tests can fix "today". */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Two in-memory accounts for the HTTP Basic demo (classroom only, see docs/security-notes.md).
     * The passwords come from the configuration and are stored as BCrypt hashes, never in plain text.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder,
                                                 @Value("${autoloc.demo.user.password}") String userPassword,
                                                 @Value("${autoloc.demo.admin.password}") String adminPassword) {
        UserDetails user = User.withUsername("student").password(encoder.encode(userPassword)).roles("USER").build();
        UserDetails admin = User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Order matters: the first matching rule wins, so specific rules come before broad ones.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/hello").hasAnyRole("USER", "ADMIN")
                        // website pages, static files and the business API: access per route is checked
                        // by AuthInterceptor (it also checks that a client only reads his own data)
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(SecurityConfig::unauthorized))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(SecurityConfig::unauthorized)
                        .accessDeniedHandler((request, response, ex) ->
                                json(response, 403, "Accès refusé : droits insuffisants")))
                .addFilterAfter(new SessionAuthenticationFilter(), BasicAuthenticationFilter.class)
                // JSON API with a SameSite=Strict session cookie: another site cannot send the cookie,
                // which is what CSRF tokens protect against. HTTP Basic is never sent automatically either.
                .csrf(csrf -> csrf.disable())
                // keep our own Cache-Control (30 days for /css and /js) instead of Spring Security's no-cache
                .headers(headers -> headers.cacheControl(cache -> cache.disable()))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable());   // logout is POST /api/auth/logout
        return http.build();
    }

    /** 401 in JSON; the Basic challenge header is only sent on the HTTP Basic demo routes (no browser pop-up on the site). */
    private static void unauthorized(HttpServletRequest request, HttpServletResponse response,
                                     Exception ex) throws IOException {
        String path = request.getRequestURI();
        if (path.equals("/api/hello") || path.startsWith("/api/admin/report")) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"AutoLoc\"");
        }
        json(response, 401, "Authentification requise");
    }

    private static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/api/**").excludePathPatterns(SPRING_SECURITY_ROUTES);
    }

    /**
     * CSS and JS are referenced with a version (?v=N) in index.html, so browsers may keep them 30 days.
     * index.html itself keeps the default handling, so a new version is picked up immediately.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl trenteJours = CacheControl.maxAge(Duration.ofDays(30)).cachePublic();
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/").setCacheControl(trenteJours);
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/").setCacheControl(trenteJours);
    }
}
