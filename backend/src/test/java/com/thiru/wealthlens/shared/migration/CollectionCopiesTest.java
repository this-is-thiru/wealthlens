package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionCopiesTest {

    @Test
    void unspecified_whenTheSmallJsonColumnsAreSpecified_leavesTheFourOfTheTradeLedger() {
        // Given — plans/W-337g.md's eighteen with embedded data, less W-337g2a1 – a3's nine and W-337g2b1's five: W-337g2b2's three and W-337g2b3's one
        Set<String> embedded = Set.of("assets", "transactions", "trade_outcomes", "profit_and_loss");

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
        assertThat(collections).hasSize(36).doesNotHaveDuplicates();
        assertThat(CollectionCopies.SOURCE).hasSize(40).containsAll(collections);
    }
}
