package dev.evidencerag.banking.application;

import dev.evidencerag.banking.domain.DemoMode;
import dev.evidencerag.banking.domain.DemoSession;
import dev.evidencerag.banking.domain.DemoSessionExpiredException;
import dev.evidencerag.banking.domain.AnalystExperienceDisabledException;
import dev.evidencerag.banking.infrastructure.BankingDemoRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankingDemoServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    void createsAnAnonymousSessionWithAnAbsoluteFourHourExpiry() {
        BankingDemoRepository repository = emptyRepository();
        when(repository.createSession(any(), eq(NOW), eq(NOW.plusSeconds(4 * 60 * 60)), eq(DemoMode.CLIENT)))
                .thenAnswer(call -> new DemoSession(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));

        var result = service(repository).start(null, DemoMode.CLIENT);

        assertEquals(NOW, result.session().createdAt());
        assertEquals(NOW.plusSeconds(4 * 60 * 60), result.session().expiresAt());
        verify(repository).createSession(any(), eq(NOW), eq(NOW.plusSeconds(4 * 60 * 60)), eq(DemoMode.CLIENT));
    }

    @Test
    void expiredSessionCannotBeRenewedByStartingAgain() {
        BankingDemoRepository repository = emptyRepository();
        UUID id = UUID.randomUUID();
        when(repository.findSession(id)).thenReturn(Optional.of(new DemoSession(id, NOW.minusSeconds(4 * 60 * 60), NOW, DemoMode.CLIENT)));

        assertThrows(DemoSessionExpiredException.class, () -> service(repository).start(id, DemoMode.ANALYST));
        verify(repository, never()).updateMode(any(), any());
        verify(repository, never()).createSession(any(), any(), any(), any());
    }

    @Test
    void changingModeKeepsTheOriginalAbsoluteExpiry() {
        BankingDemoRepository repository = emptyRepository();
        UUID id = UUID.randomUUID();
        DemoSession active = new DemoSession(id, NOW.minusSeconds(60), NOW.plusSeconds(4 * 60 * 60 - 60), DemoMode.CLIENT);
        when(repository.findSession(id)).thenReturn(Optional.of(active));

        var result = service(repository).changeMode(id, DemoMode.ANALYST);

        assertEquals(active.createdAt(), result.session().createdAt());
        assertEquals(active.expiresAt(), result.session().expiresAt());
        assertEquals(DemoMode.ANALYST, result.session().selectedMode());
        verify(repository).updateMode(id, DemoMode.ANALYST);
    }

    @Test
    void restartingDeletesOnlyTheSuppliedSessionAndCreatesAFreshOne() {
        BankingDemoRepository repository = emptyRepository();
        UUID id = UUID.randomUUID();
        when(repository.createSession(any(), eq(NOW), eq(NOW.plusSeconds(4 * 60 * 60)), eq(DemoMode.ANALYST)))
                .thenAnswer(call -> new DemoSession(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)));

        var result = service(repository).restart(id, DemoMode.ANALYST);

        verify(repository).deleteSession(id);
        assertNotEquals(id, result.session().id());
        assertEquals(DemoMode.ANALYST, result.session().selectedMode());
    }

    @Test
    void disabledAnalystCannotBeStartedOrRestartedAndDoesNotDeleteSession() {
        BankingDemoRepository repository = emptyRepository();
        UUID id = UUID.randomUUID();
        var disabled = service(repository, false);

        assertThrows(AnalystExperienceDisabledException.class, () -> disabled.start(null, DemoMode.ANALYST));
        assertThrows(AnalystExperienceDisabledException.class, () -> disabled.restart(id, DemoMode.ANALYST));

        verifyNoInteractions(repository);
    }

    @Test
    void disabledAnalystCannotBeSelectedButClientCanRecoverAnExistingAnalystSession() {
        BankingDemoRepository repository = emptyRepository();
        UUID id = UUID.randomUUID();
        DemoSession active = new DemoSession(id, NOW.minusSeconds(60), NOW.plusSeconds(4 * 60 * 60 - 60), DemoMode.ANALYST);
        when(repository.findSession(id)).thenReturn(Optional.of(active));
        var disabled = service(repository, false);

        assertThrows(AnalystExperienceDisabledException.class, () -> disabled.changeMode(id, DemoMode.ANALYST));
        assertThrows(AnalystExperienceDisabledException.class, () -> disabled.current(id));
        var recovered = disabled.changeMode(id, DemoMode.CLIENT);

        assertEquals(DemoMode.CLIENT, recovered.session().selectedMode());
        verify(repository, never()).updateMode(id, DemoMode.ANALYST);
        verify(repository).updateMode(id, DemoMode.CLIENT);
    }

    private BankingDemoRepository emptyRepository() {
        BankingDemoRepository repository = mock(BankingDemoRepository.class);
        when(repository.customers()).thenReturn(java.util.List.of());
        when(repository.accounts()).thenReturn(java.util.List.of());
        when(repository.transactions()).thenReturn(java.util.List.of());
        when(repository.alerts()).thenReturn(java.util.List.of());
        when(repository.proposals()).thenReturn(java.util.List.of());
        when(repository.policies()).thenReturn(java.util.List.of());
        return repository;
    }

    private BankingDemoService service(BankingDemoRepository repository) {
        return new BankingDemoService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private BankingDemoService service(BankingDemoRepository repository, boolean analystEnabled) {
        return new BankingDemoService(repository, Clock.fixed(NOW, ZoneOffset.UTC), analystEnabled);
    }
}
