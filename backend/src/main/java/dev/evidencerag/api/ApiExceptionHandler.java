package dev.evidencerag.api;

import jakarta.servlet.http.HttpServletRequest;
import dev.evidencerag.infrastructure.RequestIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiDtos.ErrorResponse> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiDtos.ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "O corpo da requisição é inválido.", request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiDtos.ErrorResponse> missingRoute(NoResourceFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Rota não encontrada.", request);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiDtos.ErrorResponse> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "Arquivo acima do limite permitido.", request);
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiDtos.ErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return response(status, status == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "REQUEST_REJECTED", ex.getReason(), request);
    }
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ApiDtos.ErrorResponse> providerResponse(RestClientResponseException ex, HttpServletRequest request) {
        log.warn("AI provider request failed: requestId={}, providerStatus={}, exceptionType={}",
                requestId(request), ex.getStatusCode().value(), ex.getClass().getSimpleName());
        if (ex.getStatusCode().value() == 429) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "60")
                    .body(error("AI_QUOTA_OR_RATE_LIMIT", "O provedor de IA está temporariamente limitado. Tente novamente mais tarde.", request));
        }
        return response(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_UNAVAILABLE", "O provedor de IA não conseguiu concluir a consulta.", request);
    }
    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiDtos.ErrorResponse> providerTimeout(ResourceAccessException ex, HttpServletRequest request) {
        log.warn("AI provider timed out: requestId={}, exceptionType={}", requestId(request), rootType(ex));
        return response(HttpStatus.GATEWAY_TIMEOUT, "AI_PROVIDER_TIMEOUT", "A consulta ao provedor de IA excedeu o tempo limite.", request);
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiDtos.ErrorResponse> geminiApi(ApiException ex, HttpServletRequest request) {
        log.warn("Gemini API request failed: requestId={}, providerStatus={}, providerCategory={}",
                requestId(request), ex.code(), safeCategory(ex.status()));
        log.warn("Gemini provider diagnostic: requestId={}, detail={}", requestId(request), safeProviderDetail(ex.message()));
        if (ex.code() == 429) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "60")
                    .body(error("AI_QUOTA_OR_RATE_LIMIT", "O provedor de IA está temporariamente limitado. Tente novamente mais tarde.", request));
        }
        return response(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_UNAVAILABLE", "O provedor de IA não conseguiu concluir a consulta.", request);
    }
    @ExceptionHandler(GenAiIOException.class)
    ResponseEntity<ApiDtos.ErrorResponse> geminiIo(GenAiIOException ex, HttpServletRequest request) {
        log.warn("Gemini transport failed: requestId={}, exceptionType={}", requestId(request), rootType(ex));
        return response(HttpStatus.GATEWAY_TIMEOUT, "AI_PROVIDER_TIMEOUT", "A consulta ao provedor de IA excedeu o tempo limite.", request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiDtos.ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API failure: requestId={}, exceptionType={}", requestId(request), rootType(ex));
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Não foi possível concluir a operação.", request);
    }
    private ResponseEntity<ApiDtos.ErrorResponse> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(error(code, message, request));
    }
    private ApiDtos.ErrorResponse error(String code, String message, HttpServletRequest request) {
        return new ApiDtos.ErrorResponse(code, message,
                requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.ATTRIBUTE) == null ? "unknown"
                : request.getAttribute(RequestIdFilter.ATTRIBUTE).toString();
    }

    private String rootType(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    private String safeCategory(String category) {
        return category != null && category.matches("[A-Z_]{1,40}") ? category : "UNKNOWN";
    }

    private String safeProviderDetail(String detail) {
        if (detail == null || detail.isBlank()) return "unavailable";
        String safe = detail.replaceAll("AIza[0-9A-Za-z_-]{20,}", "[REDACTED]")
                .replaceAll("(?i)(api[_ -]?key|access[_ -]?token|authorization|client[_ -]?secret)([\\s]*[:=][\\s]*[\\\"']?)[^\\s,;\\]}\\\"']+", "$1$2[REDACTED]")
                .replaceAll("(?i)(contents|prompt|document|text)([\\s:=]+)[^,;]+", "$1$2[REDACTED]")
                .replaceAll("[\\r\\n\\t]", " ");
        return safe.length() > 240 ? safe.substring(0, 240) : safe;
    }
}
