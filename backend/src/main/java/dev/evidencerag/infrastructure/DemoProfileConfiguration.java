package dev.evidencerag.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;

@Configuration
@Profile("demo")
public class DemoProfileConfiguration {
    public DemoProfileConfiguration(AppProperties properties) {
        String origin = properties.corsOrigin();
        if (origin == null || origin.isBlank() || origin.equals("*") || origin.contains(",")) {
            throw new IllegalStateException("No perfil demo, configure APP_CORS_ORIGIN com uma única origem explícita.");
        }
        URI uri;
        try { uri = URI.create(origin); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("APP_CORS_ORIGIN inválida."); }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (!(localHttp || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getPath() != null && !uri.getPath().isEmpty()) {
            throw new IllegalStateException("APP_CORS_ORIGIN deve ser uma origem HTTPS (ou localhost HTTP para testes locais).");
        }
    }
}
