package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionCopiesTest {

    @Test
    void unspecified_whenTheFlatCollectionsAreSpecified_leavesTheEighteenWithEmbeddedData() {
        // Given — the eighteen collections plans/W-337g.md counts as holding embedded data (W-337g2's)
        Set<String> embedded = Set.of("refresh_tokens", "insurances", "charge_schedules", "charge_instruments", "charge_accounts",
                "user_charges", "corporate_action", "assets", "transactions", "trade_outcomes", "profit_and_loss",
                "privacy_breach_notices", "tax_filing_drafts", "tax_draft_revisions", "tax_regime_election_history",
                "tax_draft_computations", "tax_document_confirmations", "tax_gazette_leads");

        // When
        Set<String> unspecified = CollectionCopies.unspecified();

        // Then
        assertThat(unspecified).containsExactlyInAnyOrderElementsOf(embedded);
    }

    @Test
    void specified_whenRead_namesEachSourceCollectionOnce() {
        // When
        List<String> collections = CollectionCopies.SPECIFIED.stream().map(CollectionCopy::collection).toList();

        // Then
        assertThat(collections).hasSize(22).doesNotHaveDuplicates();
        assertThat(CollectionCopies.SOURCE).hasSize(40).containsAll(collections);
    }
}
