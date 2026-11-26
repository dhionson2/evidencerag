package dev.evidencerag.banking.api;

import dev.evidencerag.banking.application.BankingAssistantService;
import dev.evidencerag.banking.application.BankingDemoTools;
import dev.evidencerag.banking.application.BankingToolEvidence;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BankingAssistantControllerTest {
    @Test
    void obtainsSessionOnlyFromHttpOnlyCookieAndRoutesWithoutTrustingClientModule() throws Exception {
        BankingAssistantService assistant = mock(BankingAssistantService.class);
        UUID sessionId = UUID.randomUUID();
        when(assistant.ask(eq(sessionId), eq("Quanto há na conta?"), anyString()))
                .thenReturn(new BankingAssistantDtos.AskResponse("GROUNDED", "Saldo fictício.",
                        List.of(new BankingToolEvidence.Evidence(UUID.randomUUID().toString(), "account", "ACC-001", "Conta ACC-001", "Saldo sintético")), null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingAssistantController(assistant)).build();

        mvc.perform(post("/api/banking/assistant").cookie(new Cookie(BankingDemoController.COOKIE_NAME, sessionId.toString()))
                        .contentType("application/json").content("{\"module\":\"FINANCE\",\"question\":\"Quanto há na conta?\",\"sessionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-Id")).andExpect(jsonPath("$.status").value("GROUNDED"))
                .andExpect(jsonPath("$.evidence[0].recordId").value("ACC-001"));
        verify(assistant).ask(eq(sessionId), eq("Quanto há na conta?"), anyString());
        verifyNoMoreInteractions(assistant);
    }

    @Test
    void rejectsRequestsWithoutTheDemoCookie() throws Exception {
        BankingAssistantService assistant = mock(BankingAssistantService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BankingAssistantController(assistant)).build();
        mvc.perform(post("/api/banking/assistant").contentType("application/json")
                        .content("{\"module\":\"FINANCE\",\"question\":\"Quanto há na conta?\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(assistant);
    }
}
