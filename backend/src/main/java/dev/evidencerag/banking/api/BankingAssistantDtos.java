package dev.evidencerag.banking.api;

import dev.evidencerag.banking.application.BankingDemoTools;
import dev.evidencerag.banking.application.BankingToolEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class BankingAssistantDtos {
    private BankingAssistantDtos() {}
    /** module is optional for older local clients; the backend never trusts it to scope available tools. */
    public record AskRequest(BankingDemoTools.Module module, @NotBlank @Size(max = 1500) String question) {}
    public record AskResponse(String status, String answer, List<BankingToolEvidence.Evidence> evidence, String message,
                              ReviewAssessment review, String answerMode, String providerIssue) {
        public AskResponse(String status, String answer, List<BankingToolEvidence.Evidence> evidence, String message) {
            this(status, answer, evidence, message, null, "AI_TOOL_LOOP", null);
        }
        public AskResponse(String status, String answer, List<BankingToolEvidence.Evidence> evidence, String message,
                           ReviewAssessment review) {
            this(status, answer, evidence, message, review, "AI_TOOL_LOOP", null);
        }
    }
    public record ReviewAssessment(List<String> structuredFacts,
                                   List<BankingToolEvidence.Evidence> documentaryEvidence,
                                   String aiInterpretation,
                                   List<String> missingInformation,
                                   List<String> recommendations) {}
    public record ConversationMessage(String id, String role, String body, String outcome, String answerMode,
                                      String providerIssue, List<BankingToolEvidence.Evidence> evidence,
                                      String createdAt) {}
    public record ConversationHistory(String experience, List<ConversationMessage> messages) {}
}
