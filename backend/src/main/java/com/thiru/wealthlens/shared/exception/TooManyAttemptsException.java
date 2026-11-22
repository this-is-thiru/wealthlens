package com.thiru.wealthlens.shared.exception;

import java.time.Duration;
import lombok.Getter;

/** Refused because a limit on attempts was reached (W-358d2): answered 429, with {@code Retry-After} from {@link #getRetryAfter()}. */
@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts; try again in " + Math.max(1, retryAfter.toSeconds()) + " seconds");
        this.retryAfter = retryAfter;
    }
}
