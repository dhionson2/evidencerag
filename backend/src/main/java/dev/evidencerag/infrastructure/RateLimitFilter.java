package dev.evidencerag.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private final ConcurrentHashMap<String, Window> clients = new ConcurrentHashMap<>();
    private final AppProperties properties;
    private final MeterRegistry metrics;

    public RateLimitFilter(AppProperties properties, MeterRegistry metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getMethod().equals("OPTIONS");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String client = request.getRemoteAddr();
        Window window = clients.compute(client, (key, current) -> current == null || now >= current.endsAt
                ? new Window(now + WINDOW_MS) : current);
        int count = window.requests.incrementAndGet();
        if (clients.size() > MAX_TRACKED_CLIENTS) clients.entrySet().removeIf(entry -> now >= entry.getValue().endsAt);
        if (count > Math.max(1, properties.rateLimitPerMinute())) {
            metrics.counter("evidencerag.requests.rejected", "reason", "rate_limit").increment();
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (window.endsAt - now + 999) / 1000)));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Limite de requisições por minuto excedido.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long endsAt;
        private final AtomicInteger requests = new AtomicInteger();
        private Window(long endsAt) { this.endsAt = endsAt; }
    }
}
