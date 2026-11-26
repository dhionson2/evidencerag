package dev.evidencerag.banking.application;

import java.util.List;
import java.util.UUID;

/** Evidence collected from tools during one request; model identifiers are resolved against this list. */
public final class BankingToolEvidence {
    private BankingToolEvidence() {}

    public record Evidence(String id, String kind, String recordId, String label, String excerpt) {}
    public record ToolResult(String status, String summary, List<String> facts, List<Evidence> evidence) {}

    public static Evidence fact(String kind, String recordId, String label, String excerpt) {
        return new Evidence(UUID.nameUUIDFromBytes((kind + ":" + recordId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                kind, recordId, label, excerpt);
    }
}
