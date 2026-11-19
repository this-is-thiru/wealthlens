package com.thiru.wealthlens.shared.exception;

/**
 * A trade that was not recorded because, on every attempt, another trade was being recorded at the same moment.
 *
 * <p>Distinct from a failure: nothing is wrong with the request and nothing was written, so the caller only needs to
 * send it again. {@code ControllerAdviser} maps this to 409, which says the request met a conflicting state rather
 * than a fault — a 500 would leave the caller unsure whether the trade was recorded (W-269, D-554).
 */
public class TradeCollisionException extends RuntimeException {

    public TradeCollisionException(String message, Throwable lastCollision) {
        super(message, lastCollision);
    }
}
