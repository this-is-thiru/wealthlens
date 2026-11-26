package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionCopiesTest {

    @Test
    void unspecified_whenEveryCollectionWithoutJsonColumnsIsSpecified_leavesTheNineWithJson() {
        // Given — plans/W-337g.md's eighteen with embedded data, less W-337g2a1's, a2's and a3's three each: the nine with JSON columns (W-337g2b*)
        Set<String> embedded = Set.of("charge_schedules", "charge_instruments", "assets", "transactions", "trade_outcomes", "profit_and_loss",
                "tax_filing_drafts", "tax_draft_revisions",
                "tax_draft_computations");

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
        assertThat(collections).hasSize(31).doesNotHaveDuplicates();
        assertThat(CollectionCopies.SOURCE).hasSize(40).containsAll(collections);
    }
}
