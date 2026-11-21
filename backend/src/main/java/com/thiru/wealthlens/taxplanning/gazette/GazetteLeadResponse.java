package com.thiru.wealthlens.taxplanning.gazette;

import java.time.LocalDateTime;
import java.util.List;

/** A Gazette notification the watch flagged, as the owner reads it (W-358c2, D-857). */
public record GazetteLeadResponse(String id, String url, int year, long number, int page, List<String> matchedTerms, List<String> possiblyAffected, String note, LocalDateTime foundAt) {

    public static GazetteLeadResponse from(GazetteLead entity) {
        return new GazetteLeadResponse(entity.getId(), entity.getUrl(), entity.getYear(), entity.getNumber(), entity.getPage(), entity.getMatchedTerms(), entity.getPossiblyAffected(), entity.getNote(), entity.getFoundAt());
    }
}
