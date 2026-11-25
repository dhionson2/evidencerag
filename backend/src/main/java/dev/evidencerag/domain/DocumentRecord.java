package dev.evidencerag.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentRecord(UUID id, String filename, String mediaType, String sha256,
                             int chunkCount, Instant createdAt) {}
