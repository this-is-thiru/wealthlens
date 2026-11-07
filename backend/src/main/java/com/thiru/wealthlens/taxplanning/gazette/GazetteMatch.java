package com.thiru.wealthlens.taxplanning.gazette;

import java.util.List;

/**
 * Why a copy is a lead: the first page at which its terms were all met, the terms it named, and the shipped rules citing a
 * section the matching pages amend or rely on (W-236 E1a, E3).
 */
public record GazetteMatch(int page, List<String> matchedTerms, List<String> possiblyAffected) {

    public GazetteMatch {
        matchedTerms = List.copyOf(matchedTerms);
        possiblyAffected = List.copyOf(possiblyAffected);
    }
}
