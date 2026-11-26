package dev.evidencerag.banking.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Process-wide rolling 60s token admission budget; values are estimates until provider usage arrives. */
final class RollingTokenBudget {
    private final int limit;
    private final Clock clock;
    private final Deque<Reservation> reservations = new ArrayDeque<>();

    RollingTokenBudget(int limit) { this(limit, Clock.systemUTC()); }
    RollingTokenBudget(int limit, Clock clock) { this.limit = Math.max(1000, limit); this.clock = clock; }

    synchronized Reservation reserve(int tokens) {
        purge();
        int requested = Math.max(1, tokens);
        long used = reservations.stream().mapToLong(r -> r.tokens).sum();
        if (used + requested > limit) throw new TokenBudgetExceededException((int) Math.min(Integer.MAX_VALUE, used), requested, limit);
        Reservation reservation = new Reservation(clock.instant(), requested);
        reservations.addLast(reservation);
        return reservation;
    }

    synchronized void settle(Reservation reservation, int actualTokens) {
        reservation.tokens = Math.max(1, actualTokens);
        purge();
    }

    private void purge() {
        Instant cutoff = clock.instant().minusSeconds(60);
        while (!reservations.isEmpty() && reservations.peekFirst().createdAt.isBefore(cutoff)) reservations.removeFirst();
    }

    static final class Reservation {
        private final Instant createdAt;
        private volatile int tokens;
        private Reservation(Instant createdAt, int tokens) { this.createdAt = createdAt; this.tokens = tokens; }
    }

    static final class TokenBudgetExceededException extends RuntimeException {
        private final int usedTokens;
        private final int requestedTokens;
        private final int limitTokens;

        TokenBudgetExceededException(int usedTokens, int requestedTokens, int limitTokens) {
            super("Local rolling model token budget exceeded");
            this.usedTokens = usedTokens;
            this.requestedTokens = requestedTokens;
            this.limitTokens = limitTokens;
        }

        int usedTokens() { return usedTokens; }
        int requestedTokens() { return requestedTokens; }
        int limitTokens() { return limitTokens; }
    }
}
