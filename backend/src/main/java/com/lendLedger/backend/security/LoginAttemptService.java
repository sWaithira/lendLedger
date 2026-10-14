package com.lendLedger.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Locks an email for the rest of a 15-minute window after 5 bad passwords (ADR-06, F-001).
 * In memory, so it resets on restart and covers one instance only; production would use Redis or the database.
 * Trade-off: an attacker can lock a known email out. Accepted for the demo.
 */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int PRUNE_THRESHOLD = 10_000;

    private record Attempts(int count, Instant windowStart) { }

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public boolean isLocked(String email) {
        Attempts a = attempts.get(key(email));
        return a != null && !expired(a) && a.count() >= MAX_FAILURES;
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        Instant now = clock.instant();
        if (attempts.size() > PRUNE_THRESHOLD) attempts.values().removeIf(this::expired);
        attempts.compute(key(event.getAuthentication().getName()), (k, old) ->
                (old == null || expired(old))
                        ? new Attempts(1, now)
                        : new Attempts(old.count() + 1, old.windowStart()));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.remove(key(event.getAuthentication().getName()));
    }

    private boolean expired(Attempts a) {
        return a.windowStart().plus(WINDOW).isBefore(clock.instant());
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}