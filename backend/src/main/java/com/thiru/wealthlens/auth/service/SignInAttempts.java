package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.config.SignInLimit;
import com.thiru.wealthlens.shared.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The attempts counted against {@link SignInLimit} (W-358d2, D-884): failed sign-ins per address and client, so a guesser is slowed
 * while a person cannot be locked out by someone else failing on purpose, and calls per client to the public auth routes. Kept in
 * memory (C1a): one instance serves, and a restart clears the counts.
 */
@Component
@RequiredArgsConstructor
public class SignInAttempts {

    private final SignInLimit limit;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    /** Counts a call by {@code client}; refuses it when the client is over its limit. */
    public void call(String client) {
        Instant now = clock.instant();
        Deque<Instant> recent = recent(calls, client, now);
        synchronized (recent) {
            recent.addLast(now);
            if (recent.size() > limit.callsPerClient()) {
                throw new TooManyAttemptsException(untilFree(recent, now));
            }
        }
    }

    /** Refuses a sign-in for {@code address} from {@code client} once its failures reach the limit, whatever the password. */
    public void requireAllowed(String address, String client) {
        Instant now = clock.instant();
        Deque<Instant> recent = recent(failures, pair(address, client), now);
        synchronized (recent) {
            if (recent.size() >= limit.failuresPerAddress()) {
                throw new TooManyAttemptsException(untilFree(recent, now));
            }
        }
    }

    public void recordFailure(String address, String client) {
        Instant now = clock.instant();
        Deque<Instant> recent = recent(failures, pair(address, client), now);
        synchronized (recent) {
            recent.addLast(now);
        }
    }

    /** A successful sign-in starts the count again. */
    public void clear(String address, String client) {
        failures.remove(pair(address, client));
    }

    private Deque<Instant> recent(Map<String, Deque<Instant>> counts, String key, Instant now) {
        Deque<Instant> recent = counts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && !recent.peekFirst().plus(limit.window()).isAfter(now)) {
                recent.pollFirst();
            }
        }
        return recent;
    }

    private Duration untilFree(Deque<Instant> recent, Instant now) {
        return Duration.between(now, recent.peekFirst().plus(limit.window()));
    }

    private static String pair(String address, String client) {
        return String.valueOf(address).trim().toLowerCase(Locale.ROOT) + "|" + client;
    }
}
