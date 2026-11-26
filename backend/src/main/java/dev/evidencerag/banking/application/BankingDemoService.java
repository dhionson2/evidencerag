package dev.evidencerag.banking.application;

import dev.evidencerag.banking.domain.BankDemoData;
import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import dev.evidencerag.banking.domain.DemoSessionExpiredException;
import dev.evidencerag.banking.domain.AnalystExperienceDisabledException;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BankingDemoService {
    public static final Duration SESSION_TTL = Duration.ofHours(4);
    private static final long OPPORTUNISTIC_CLEANUP_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

    private final BankingDemoRepository repository;
    private final Clock clock;
    private final boolean analystEnabled;
    private final AtomicLong lastCleanupMillis = new AtomicLong();

    @Autowired
    public BankingDemoService(BankingDemoRepository repository,
            @Value("${app.banking.analyst-enabled:false}") boolean analystEnabled) {
        this(repository, Clock.systemUTC(), analystEnabled);
    }

    public BankingDemoService(BankingDemoRepository repository) {
        this(repository, Clock.systemUTC(), true);
    }

    public BankingDemoService(BankingDemoRepository repository, Clock clock) {
        this(repository, clock, true);
    }

    public BankingDemoService(BankingDemoRepository repository, Clock clock, boolean analystEnabled) {
        this.repository = repository;
        this.clock = clock;
        this.analystEnabled = analystEnabled;
    }

    public boolean analystEnabled() { return analystEnabled; }

    @Transactional
    public BankDemoData.Snapshot start(UUID sessionId, DemoMode mode) {
        ensureModeEnabled(mode);
        Instant now = clock.instant();
        var existing = sessionId == null ? java.util.Optional.<DemoSession>empty() : repository.findSession(sessionId);
        if (existing.isPresent() && existing.get().isExpiredAt(now)) throw new DemoSessionExpiredException();
        cleanupOpportunistically();
        DemoSession session;
        if (existing.isPresent()) {
            repository.updateMode(sessionId, mode);
            session = new DemoSession(sessionId, existing.get().createdAt(), existing.get().expiresAt(), mode);
        } else {
            session = createSession(now, mode);
        }
        return snapshot(session);
    }

    @Transactional
    public BankDemoData.Snapshot current(UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("A sessão da demonstração não foi iniciada.");
        Instant now = clock.instant();
        var existing = repository.findSession(sessionId);
        if (existing.isEmpty()) throw new IllegalArgumentException("A sessão da demonstração não foi encontrada.");
        if (existing.get().isExpiredAt(now)) throw new DemoSessionExpiredException();
        ensureModeEnabled(existing.get().selectedMode());
        cleanupOpportunistically();
        return snapshot(existing.get());
    }

    @Transactional
    public BankDemoData.Snapshot changeMode(UUID sessionId, DemoMode mode) {
        ensureModeEnabled(mode);
        DemoSession active = requireActiveSession(sessionId);
        cleanupOpportunistically();
        repository.updateMode(sessionId, mode);
        return snapshot(new DemoSession(active.id(), active.createdAt(), active.expiresAt(), mode));
    }

    @Transactional
    public BankDemoData.Snapshot restart(UUID sessionId, DemoMode mode) {
        ensureModeEnabled(mode);
        cleanupOpportunistically();
        if (sessionId != null) repository.deleteSession(sessionId);
        return snapshot(createSession(clock.instant(), mode));
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT15M")
    @Transactional
    public int cleanExpiredSessions() {
        return repository.deleteExpiredSessions(clock.instant());
    }

    private DemoSession requireActiveSession(UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("A sessão da demonstração não foi iniciada.");
        Instant now = clock.instant();
        var existing = repository.findSession(sessionId);
        if (existing.isEmpty()) throw new IllegalArgumentException("A sessão da demonstração não foi encontrada.");
        if (existing.get().isExpiredAt(now)) throw new DemoSessionExpiredException();
        return existing.get();
    }

    private DemoSession createSession(Instant now, DemoMode mode) {
        return repository.createSession(UUID.randomUUID(), now, now.plus(SESSION_TTL), mode);
    }

    private void ensureModeEnabled(DemoMode mode) {
        if (mode == DemoMode.ANALYST && !analystEnabled) throw new AnalystExperienceDisabledException();
    }

    private BankDemoData.Snapshot snapshot(DemoSession session) {
        return new BankDemoData.Snapshot(session, repository.customers(), repository.accounts(), repository.transactions(),
                repository.alerts(), repository.proposals(), repository.policies());
    }

    private void cleanupOpportunistically() {
        long now = clock.millis();
        long previous = lastCleanupMillis.get();
        if (now - previous >= OPPORTUNISTIC_CLEANUP_INTERVAL_MS && lastCleanupMillis.compareAndSet(previous, now)) {
            repository.deleteExpiredSessions(clock.instant());
        }
    }
}
