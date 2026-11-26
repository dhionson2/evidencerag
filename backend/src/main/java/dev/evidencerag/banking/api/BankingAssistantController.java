package dev.evidencerag.banking.api;

import dev.evidencerag.banking.application.BankingAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/banking/assistant")
public class BankingAssistantController {
    private final BankingAssistantService assistant;
    public BankingAssistantController(BankingAssistantService assistant) { this.assistant = assistant; }

    @PostMapping
    public BankingAssistantDtos.AskResponse ask(@Valid @RequestBody BankingAssistantDtos.AskRequest request, HttpServletRequest http,
                                                  HttpServletResponse response) {
        UUID id = cookieSessionId(http);
        if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo não iniciada.");
        String correlationId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", correlationId);
        return assistant.ask(id, request.question(), correlationId);
    }

    @GetMapping("/history")
    public BankingAssistantDtos.ConversationHistory history(HttpServletRequest http) {
        UUID id = cookieSessionId(http);
        if (id == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo não iniciada.");
        return assistant.history(id);
    }

    private UUID cookieSessionId(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> BankingDemoController.COOKIE_NAME.equals(c.getName())).findFirst()
                .map(c -> { try { return UUID.fromString(c.getValue()); } catch (IllegalArgumentException ex) { return null; } }).orElse(null);
    }
}
