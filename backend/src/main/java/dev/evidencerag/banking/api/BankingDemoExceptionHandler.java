package dev.evidencerag.banking.api;

import dev.evidencerag.banking.domain.DemoSessionExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {BankingDemoController.class, BankingAssistantController.class})
public class BankingDemoExceptionHandler {
    @ExceptionHandler(DemoSessionExpiredException.class)
    ResponseEntity<BankingDemoDtos.ErrorResponse> expired(DemoSessionExpiredException error) {
        return ResponseEntity.status(HttpStatus.GONE).body(new BankingDemoDtos.ErrorResponse("SESSION_EXPIRED", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<BankingDemoDtos.ErrorResponse> invalidSession(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new BankingDemoDtos.ErrorResponse("SESSION_NOT_FOUND", error.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<BankingDemoDtos.ErrorResponse> status(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        String code = switch (status) {
            case 429 -> error.getReason() != null && error.getReason().contains("cota do provedor")
                    ? "AI_PROVIDER_QUOTA_EXCEEDED" : "REQUEST_LIMIT_EXCEEDED";
            case 502 -> "AI_PROVIDER_ERROR";
            case 503 -> "AI_UNAVAILABLE";
            case 504 -> "AI_PROVIDER_TIMEOUT";
            case 410 -> "SESSION_EXPIRED";
            case 403 -> "MODULE_NOT_AVAILABLE";
            case 400 -> "INVALID_REQUEST";
            default -> status == 404 ? "DEMO_NOT_STARTED" : "REQUEST_REJECTED";
        };
        return ResponseEntity.status(error.getStatusCode()).body(new BankingDemoDtos.ErrorResponse(code, error.getReason()));
    }
}
