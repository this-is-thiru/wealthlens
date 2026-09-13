package com.thiru.wealthlens.shared.exception;

/**
 * A request that is well formed and permitted, but refused because the capability behind it is
 * administratively switched off.
 *
 * <p>Distinct from {@link BadRequestException} on purpose. The caller did nothing wrong and does not
 * need to change anything — an operator does — so answering 400 would send them looking for a fault
 * in their own request, and 500 would send somebody looking for a crash that did not happen.
 * {@code ControllerAdviser} maps this to 503, which is the one status that says "try again once
 * somebody turns it back on".
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
