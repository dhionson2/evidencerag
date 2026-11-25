package dev.evidencerag.infrastructure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GeminiProviderActiveCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return "google-genai".equals(environment.getProperty("spring.ai.model.chat"))
                || "google-genai".equals(environment.getProperty("spring.ai.model.embedding"))
                || "google-genai".equals(environment.getProperty("spring.ai.model.embedding.text"));
    }
}
