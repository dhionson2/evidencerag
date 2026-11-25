package dev.evidencerag.api;

import dev.evidencerag.application.RagQueryService;
import dev.evidencerag.domain.RagAnswer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class QueryControllerTest {
    @Test void returnsStructuredInsufficientEvidenceResponse() throws Exception {
        RagQueryService service = mock(RagQueryService.class);
        when(service.ask("Qual a taxa?" )).thenReturn(new RagAnswer(UUID.randomUUID(), "INSUFFICIENT_EVIDENCE",
                "Não encontrei evidências documentais suficientes para responder com segurança.", List.of()));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = standaloneSetup(new QueryController(service)).setValidator(validator).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/query").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Qual a taxa?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.citations").isEmpty()).andExpect(jsonPath("$.runId").isNotEmpty());
    }
    @Test void rejectsBlankQuestion() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mvc = standaloneSetup(new QueryController(mock(RagQueryService.class))).setValidator(validator).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/query").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
