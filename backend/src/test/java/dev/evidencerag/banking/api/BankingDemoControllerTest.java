package dev.evidencerag.banking.api;

import dev.evidencerag.banking.application.BankingDemoService;
import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import dev.evidencerag.banking.domain.AnalystExperienceDisabledException;
import dev.evidencerag.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankingDemoControllerTest {
    @Test
    void startsWithAnHttpOnlyCookieAndDoesNotReturnTheSessionTokenInJson() throws Exception {
        BankingDemoService service = mock(BankingDemoService.class);
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-25T12:00:00Z");
        var session = new DemoSession(id, created, created.plusSeconds(4 * 60 * 60), DemoMode.CLIENT);
        when(service.start(isNull(), eq(DemoMode.CLIENT))).thenReturn(new BankDemoData.Snapshot(session, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingDemoController(service, true))
                .setControllerAdvice(new BankingDemoExceptionHandler()).build();

        mvc.perform(post("/api/banking/session").header("Origin", "http://localhost:15175")
                        .contentType("application/json").content("{\"mode\":\"CLIENT\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("BANK_DEMO_SESSION=" + id),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("Secure"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"),
                        org.hamcrest.Matchers.containsString("Path=/"))))
                .andExpect(jsonPath("$.session.expiresAt").value("2026-11-25T16:00:00Z"))
                .andExpect(jsonPath("$.session.id").doesNotExist());
    }

    @Test
    void changesModeForFrontendOriginUsingTheSessionCookie() throws Exception {
        BankingDemoService service = mock(BankingDemoService.class);
        when(service.analystEnabled()).thenReturn(true);
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-25T12:00:00Z");
        var session = new DemoSession(id, created, created.plusSeconds(4 * 60 * 60), DemoMode.ANALYST);
        when(service.changeMode(id, DemoMode.ANALYST)).thenReturn(new BankDemoData.Snapshot(session, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingDemoController(service, false))
                .setControllerAdvice(new ApiExceptionHandler(), new BankingDemoExceptionHandler()).build();

        mvc.perform(put("/api/banking/mode").header("Origin", "http://localhost:15175")
                        .cookie(new jakarta.servlet.http.Cookie(BankingDemoController.COOKIE_NAME, id.toString()))
                        .contentType("application/json").content("{\"mode\":\"ANALYST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.selectedMode").value("ANALYST"));
        verify(service).changeMode(id, DemoMode.ANALYST);
    }

    @Test
    void readsSessionOnlyFromCookieAndNotFromARequestParameter() throws Exception {
        BankingDemoService service = mock(BankingDemoService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingDemoController(service, false))
                .setControllerAdvice(new ApiExceptionHandler(), new BankingDemoExceptionHandler()).build();

        mvc.perform(get("/api/banking/demo").param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void analystSessionRouteReturnsDisabledWithoutSettingACookie() throws Exception {
        BankingDemoService service = mock(BankingDemoService.class);
        when(service.start(isNull(), eq(DemoMode.ANALYST))).thenThrow(new AnalystExperienceDisabledException());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingDemoController(service, false))
                .setControllerAdvice(new ApiExceptionHandler(), new BankingDemoExceptionHandler()).build();

        mvc.perform(post("/api/banking/session").contentType("application/json").content("{\"mode\":\"ANALYST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPERIENCE_DISABLED"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void analystModeRouteReturnsDisabledAndClientCanReadOnlyItsAllowedData() throws Exception {
        BankingDemoService service = mock(BankingDemoService.class);
        when(service.analystEnabled()).thenReturn(false);
        UUID id = UUID.randomUUID();
        var session = new DemoSession(id, Instant.parse("2026-11-25T12:00:00Z"), Instant.parse("2026-11-25T16:00:00Z"), DemoMode.CLIENT);
        var txClient = new BankDemoData.Transaction("tx-client", "ACC-001", Instant.parse("2026-11-25T10:00:00Z"), "Compra fictícia", "Compras", java.math.BigDecimal.TEN, "DEBIT", "ALT-001");
        var txAnalyst = new BankDemoData.Transaction("tx-other", "ACC-002", Instant.parse("2026-11-25T09:00:00Z"), "Outro débito", "Compras", java.math.BigDecimal.ONE, "DEBIT", "ALT-002");
        var snapshot = new BankDemoData.Snapshot(session,
                List.of(new BankDemoData.Customer("CUST-001", "Cliente A", "SC-01"), new BankDemoData.Customer("CUST-002", "Cliente B", "SC-02")),
                List.of(new BankDemoData.Account("ACC-001", "CUST-001", "Cliente A", "Conta", "BRL", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN),
                        new BankDemoData.Account("ACC-002", "CUST-002", "Cliente B", "Conta", "BRL", java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE)),
                List.of(txClient, txAnalyst),
                List.of(new BankDemoData.Alert("ALT-001", "ALERT-4800", "tx-client", "CR-102", "RULE-A", "Alerta Cliente"),
                        new BankDemoData.Alert("ALT-002", "ALERT-OTHER", "tx-other", "CR-102", "RULE-B", "Alerta Analista")),
                List.of(new BankDemoData.Proposal("CR-102", "Empresa", java.math.BigDecimal.TEN, 12, 12, 5, 5, null, "POL-CRED")),
                List.of(new BankDemoData.Policy("POL-CRED", "2.1", "Crédito", "4.2", java.time.LocalDate.now(), "Regra de crédito"),
                        new BankDemoData.Policy("POL-ALERT", "1.3", "Alerta", "2.1", java.time.LocalDate.now(), "Regra de alerta")));
        when(service.current(id)).thenReturn(snapshot);
        when(service.changeMode(id, DemoMode.ANALYST)).thenThrow(new AnalystExperienceDisabledException());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingDemoController(service, false))
                .setControllerAdvice(new ApiExceptionHandler(), new BankingDemoExceptionHandler()).build();
        var cookie = new jakarta.servlet.http.Cookie(BankingDemoController.COOKIE_NAME, id.toString());

        mvc.perform(put("/api/banking/mode").cookie(cookie).contentType("application/json").content("{\"mode\":\"ANALYST\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPERIENCE_DISABLED"));
        mvc.perform(get("/api/banking/demo").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers.length()").value(1))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].id").value("ACC-001"))
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].id").value("tx-client"))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].relatedProposalId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.proposals").isEmpty())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].id").value("POL-ALERT"));
    }
}
