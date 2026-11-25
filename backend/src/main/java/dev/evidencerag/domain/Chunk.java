package dev.evidencerag.domain;

import java.util.UUID;

public record Chunk(UUID id, int index, Integer pageNumber, int startOffset, int endOffset, String text) {}
