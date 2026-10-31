package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The part of the capital-gains head a row puts a bucket in (D-289): which gains the special-rate provisions charge is
 * the law's, so each row of {@code RS-CG-PART} states the bucket facts it covers as conditions and the part here, and a
 * changed condition is new data rather than new Java.
 *
 * @param part the part the row's buckets fall in
 */
public record BucketPart(CapitalGainsPart part) implements RuleDefinition {

    public BucketPart {
        if (part == null) {
            throw new IllegalArgumentException("A bucket-part row must name its part");
        }
    }
}
