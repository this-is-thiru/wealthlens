package com.thiru.wealthlens.taxplanning.workspace;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Saves a draft again when the save collided with another save of the same revision (W-310, D-620).
 *
 * <p>Two saves at one revision both insert that revision number, which is unique per draft. The loser's insert
 * fails while the winner's transaction is still open, so reading the draft at that moment still shows the old
 * revision and no competing change — a 409 built then would name no conflicting path, which {@code DR 11} F5's
 * client takes as "nothing overlaps". Running the save again instead lands in {@link DraftService#patch}'s own
 * revision check once the winner commits, or applies if the winner rolled back; the failed attempt left nothing,
 * because its transaction rolled back.
 *
 * <p>Sits outside {@link DraftService}'s transactions, so each attempt is a transaction of its own.
 */
@Component
@RequiredArgsConstructor
public class DraftSaveRetry {

    /**
     * The policy of {@code TradeCollisionRetry} (W-269): at most 20 + 40 + 80 + 160 + 320 ms of waiting, far inside a
     * client's timeout. A choice, not a sourced figure (W-310 plan).
     */
    private static final RetryPolicy ON_COLLISION = RetryPolicy.builder()
            .maxRetries(5)
            .delay(Duration.ofMillis(20))
            .multiplier(2)
            .maxDelay(Duration.ofMillis(500))
            .jitter(Duration.ofMillis(10))
            .predicate(DataIntegrityViolationException.class::isInstance)
            .build();

    private final RetryTemplate retryTemplate = new RetryTemplate(ON_COLLISION);
    private final DraftService draftService;

    /**
     * {@link DraftService#patch}, run again while it collides with another save of the same revision.
     *
     * @throws DraftConflictException as {@code patch} does, or when every attempt collided, saying the other save had
     *                                not finished
     */
    public DraftView patch(String email, String draftId, int expectedRevision, JsonNode patch) {
        try {
            return retryTemplate.execute(() -> draftService.patch(email, draftId, expectedRevision, patch));
        } catch (RetryException stopped) {
            Throwable last = stopped.getCause();
            if (last instanceof DataIntegrityViolationException) {
                throw draftService.conflictAfterRace(email, draftId);
            }
            if (last instanceof RuntimeException unchecked) {
                throw unchecked;
            }
            if (last instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("A draft save failed with a checked exception, which none declares", last);
        }
    }
}
