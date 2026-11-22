package com.thiru.wealthlens.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.auth.config.SignInLimit;
import com.thiru.wealthlens.shared.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** {@link SignInAttempts} on a clock the test moves: 5 failures and 30 calls in 15 minutes (W-358d2, D-884 C1a). */
class SignInAttemptsTest {

    private static final String CLIENT = "10.0.0.1";

    private final MovingClock clock = new MovingClock();
    private final SignInAttempts attempts = new SignInAttempts(new SignInLimit(5, 30, Duration.ofMinutes(15)), clock);

    @Test
    void requireAllowed_whenFiveFailuresAreInTheWindow_refusesUntilTheFirstLeavesIt() {
        // Given — failures at 0, 1, 2, 3 and 4 minutes
        for (int minute = 0; minute < 5; minute++) {
            attempts.recordFailure("anu@example.com", CLIENT);
            clock.advance(Duration.ofMinutes(1));
        }

        // When / Then — at 5 minutes the first failure leaves the window at 15: 10 minutes to wait
        assertThatThrownBy(() -> attempts.requireAllowed("anu@example.com", CLIENT)).isInstanceOfSatisfying(TooManyAttemptsException.class,
                refused -> assertThat(refused.getRetryAfter()).isEqualTo(Duration.ofMinutes(10)));
        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> attempts.requireAllowed("anu@example.com", CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void requireAllowed_whenFourFailures_allows() {
        // Given
        for (int attempt = 0; attempt < 4; attempt++) {
            attempts.recordFailure("anu@example.com", CLIENT);
        }

        // When / Then
        assertThatCode(() -> attempts.requireAllowed("anu@example.com", CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void requireAllowed_whenTheFailuresWereAnotherClientsOrAddresss_allows() {
        // Given — five failures for the address from another client, and five for another address from this one
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.recordFailure("anu@example.com", "10.0.0.2");
            attempts.recordFailure("bala@example.com", CLIENT);
        }

        // When / Then
        assertThatCode(() -> attempts.requireAllowed("anu@example.com", CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void requireAllowed_whenTheAddressDiffersOnlyInCaseOrSpaces_countsItAsOne() {
        // Given
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.recordFailure(attempt % 2 == 0 ? "Anu@Example.com" : " anu@example.com ", CLIENT);
        }

        // When / Then
        assertThatThrownBy(() -> attempts.requireAllowed("anu@example.com", CLIENT)).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void clear_whenTheAddressSignsIn_startsTheCountAgain() {
        // Given
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.recordFailure("anu@example.com", CLIENT);
        }

        // When
        attempts.clear("anu@example.com", CLIENT);

        // Then
        assertThatCode(() -> attempts.requireAllowed("anu@example.com", CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void call_whenTheThirtyFirstIsInTheWindow_refusesItAndAllowsOnceTheFirstLeaves() {
        // Given — thirty calls at one moment
        for (int call = 0; call < 30; call++) {
            attempts.call(CLIENT);
        }

        // When / Then
        assertThatThrownBy(() -> attempts.call(CLIENT)).isInstanceOfSatisfying(TooManyAttemptsException.class,
                refused -> assertThat(refused.getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> attempts.call(CLIENT)).doesNotThrowAnyException();
        assertThatCode(() -> attempts.call("10.0.0.2")).doesNotThrowAnyException();
    }

    /** A clock the test moves forward. */
    private static final class MovingClock extends Clock {

        private Instant instant = Instant.parse("2027-01-01T08:00:00Z");

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
