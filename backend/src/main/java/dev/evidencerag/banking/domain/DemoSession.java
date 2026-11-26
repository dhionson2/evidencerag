package dev.evidencerag.banking.domain;

import java.time.Instant;
import java.util.UUID;

public record DemoSession(UUID id, Instant createdAt, Instant expiresAt, DemoMode selectedMode) {
    public boolean isExpiredAt(Instant now) { return !expiresAt.isAfter(now); }
}
