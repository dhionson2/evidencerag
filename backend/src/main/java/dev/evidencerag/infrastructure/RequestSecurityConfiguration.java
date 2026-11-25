package dev.evidencerag.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class RequestSecurityConfiguration {
    @Bean
    WebMvcConfigurer corsConfigurer(AppProperties properties) {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(properties.corsOrigin())
                        .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-Api-Key", "X-Request-Id").maxAge(3600);
            }
        };
    }
    @Bean
    OncePerRequestFilter localTokenFilter(AppProperties properties) {
        return new OncePerRequestFilter() {
            @Override protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                return properties.security() == null || properties.security().token() == null || properties.security().token().isBlank()
                        || request.getMethod().equals("OPTIONS") || path.startsWith("/actuator/health")
                        || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
            }
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String expected = properties.security().token();
                String received = request.getHeader("X-Api-Key");
                if (received == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Token local inválido ou ausente.\"}");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }
}
