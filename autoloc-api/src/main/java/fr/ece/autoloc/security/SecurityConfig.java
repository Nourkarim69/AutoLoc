package fr.ece.autoloc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;

/**
 * Web setup: BCrypt for passwords, the interceptor that protects the REST API,
 * and browser caching of the CSS / JS files.
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/api/**");
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
