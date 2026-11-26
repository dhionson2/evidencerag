package dev.evidencerag.banking.domain;

public final class AnalystExperienceDisabledException extends RuntimeException {
    public AnalystExperienceDisabledException() {
        super("A experiência Analista não está disponível nesta versão.");
    }
}
