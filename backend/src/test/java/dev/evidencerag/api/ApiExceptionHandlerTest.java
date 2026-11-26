package dev.evidencerag.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import dev.evidencerag.banking.domain.AnalystExperienceDisabledException;
import dev.evidencerag.banking.domain.DemoSessionExpiredException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test void unmappedAdminRouteReturnsSanitizedNotFound() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().missingRoute(
                new NoResourceFoundException(HttpMethod.GET, "/api/documents", "/api/documents"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Rota não encontrada.");
    }
    @Test void geminiQuotaErrorsAreSanitizedAndRetryable() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().geminiApi(new ApiException(429, "RESOURCE_EXHAUSTED", "FAKE_SECRET provider response"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody().message()).doesNotContain("FAKE_SECRET");
    }
    @Test void geminiNetworkTimeoutIsSanitized() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().geminiIo(new GenAiIOException("FAKE_SECRET timeout"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(504);
        assertThat(response.getBody().message()).doesNotContain("FAKE_SECRET");
    }
    @Test void disabledAnalystExperienceIsNotFoundRatherThanInternalError() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().analystDisabled(new AnalystExperienceDisabledException(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("EXPERIENCE_DISABLED");
    }
    @Test void expiredDemoSessionIsGoneRatherThanInternalError() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().demoSessionExpired(new DemoSessionExpiredException(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(410);
        assertThat(response.getBody().code()).isEqualTo("SESSION_EXPIRED");
    }
}
