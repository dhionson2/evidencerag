package dev.evidencerag.banking.api;

import dev.evidencerag.banking.application.BankingDemoService;
import dev.evidencerag.banking.domain.DemoMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/banking")
public class BankingDemoController {
    public static final String COOKIE_NAME = "BANK_DEMO_SESSION";
    private final BankingDemoService service;
    private final boolean secureCookie;

    public BankingDemoController(BankingDemoService service,
                                 @Value("${app.banking.session-cookie-secure:false}") boolean secureCookie) {
        this.service = service;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/demo")
    public BankingDemoDtos.DemoResponse current(HttpServletRequest request) {
        UUID sessionId = cookieSessionId(request);
        if (sessionId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo não iniciada.");
        return BankingDemoDtos.DemoResponse.from(service.current(sessionId), service.analystEnabled());
    }

    @PostMapping("/session")
    public BankingDemoDtos.DemoResponse start(@Valid @RequestBody BankingDemoDtos.StartRequest body,
                                               HttpServletRequest request, HttpServletResponse response) {
        var data = service.start(cookieSessionId(request), body.mode());
        setSessionCookie(response, data.session().id(), data.session().expiresAt());
        return BankingDemoDtos.DemoResponse.from(data, service.analystEnabled());
    }

    @PostMapping("/session/restart")
    public BankingDemoDtos.DemoResponse restart(@RequestBody(required = false) BankingDemoDtos.StartRequest body,
                                                 HttpServletRequest request, HttpServletResponse response) {
        var data = service.restart(cookieSessionId(request), body == null || body.mode() == null ? DemoMode.CLIENT : body.mode());
        setSessionCookie(response, data.session().id(), data.session().expiresAt());
        return BankingDemoDtos.DemoResponse.from(data, service.analystEnabled());
    }

    @PutMapping("/mode")
    public BankingDemoDtos.DemoResponse changeMode(@Valid @RequestBody BankingDemoDtos.ModeRequest body,
                                                    HttpServletRequest request) {
        return BankingDemoDtos.DemoResponse.from(service.changeMode(requiredSessionId(request), body.mode()), service.analystEnabled());
    }

    private UUID requiredSessionId(HttpServletRequest request) {
        UUID id = cookieSessionId(request);
        if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo não iniciada.");
        return id;
    }

    private UUID cookieSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> COOKIE_NAME.equals(cookie.getName())).findFirst()
                .map(cookie -> { try { return UUID.fromString(cookie.getValue()); } catch (IllegalArgumentException ignored) { return null; } })
                .orElse(null);
    }

    private void setSessionCookie(HttpServletResponse response, UUID id, java.time.Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(java.time.Instant.now(), expiresAt).getSeconds());
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, id.toString())
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(Duration.ofSeconds(maxAge)).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
