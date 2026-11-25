package dev.evidencerag.domain;

import java.util.List;
import java.util.UUID;

public record RagAnswer(UUID runId, String status, String answer, List<Citation> citations) {}
