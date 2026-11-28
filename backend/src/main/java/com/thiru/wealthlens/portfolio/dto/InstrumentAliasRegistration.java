package com.thiru.wealthlens.portfolio.dto;

import java.util.List;

/**
 * What one registration did (W-440b): each alias written, and each already on file naming the same code, so a second run of the same
 * list reports everything skipped and writes nothing.
 */
public record InstrumentAliasRegistration(List<String> created, List<String> skipped) {

    public InstrumentAliasRegistration {
        created = List.copyOf(created);
        skipped = List.copyOf(skipped);
    }
}
