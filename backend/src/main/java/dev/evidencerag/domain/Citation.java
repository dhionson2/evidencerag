package dev.evidencerag.domain;

import java.util.UUID;

public record Citation(UUID documentId, String documentName, UUID chunkId, Integer pageNumber,
                       Double similarity, String excerpt) {}
