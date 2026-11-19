package com.thiru.wealthlens.taxplanning.profile;

/**
 * A request that writes a record held under a profile, validating what it carries once, on the way in.
 *
 * @param <T> the record it writes
 */
interface ProfileReferenceRequest<T> {

    /** {@code record} with this request's facts set on it. */
    T applyTo(T record);
}
