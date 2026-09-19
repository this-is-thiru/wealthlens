package com.thiru.wealthlens.taxrules.temporal;

/**
 * A rule was read against an axis whose date the computation never supplied.
 *
 * <p>This exception is the whole point of {@code D-TEMPORALKEY}. A node evaluating s.197(3) must
 * supply an acquisition date, and forgetting to must be an <b>error</b> rather than a silent
 * fallback to the tax year — because falling back would answer a question about an asset bought in
 * 2019 using this year's law, confidently and wrongly.
 */
public class MissingTemporalCoordinateException extends RuntimeException {

    public MissingTemporalCoordinateException(TemporalAxis axis) {
        super("No date supplied for temporal axis " + axis
                + "; a rule keyed on it cannot be read without one");
    }
}
