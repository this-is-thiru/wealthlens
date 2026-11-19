package com.thiru.wealthlens.taxplanning.jobs;

/**
 * What a run on request did (W-336): {@code ran} is false when another run held the job's lease, so nothing ran this time.
 *
 * @param job the job's name, as {@link TaxJob#name()}
 * @param ran whether this request ran it
 */
public record JobRunReceipt(String job, boolean ran) {
}
