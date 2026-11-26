package dev.evidencerag.banking.domain;

public class DemoSessionExpiredException extends RuntimeException {
    public DemoSessionExpiredException() { super("A sessão da demonstração expirou. Inicie uma nova demonstração."); }
}
