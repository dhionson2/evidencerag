package dev.evidencerag.domain;

public class InsufficientEvidenceException extends RuntimeException {
    public InsufficientEvidenceException(String message) { super(message); }
}
