package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionCopiesTest {

    @Test
    void unspecified_whenEveryCollectionIsSpecified_isEmpty() {
        // Given — the forty m1.6.1 collections, every one specified by W-337g1 – W-337g2b3

        // When
        Set<String> unspecified = CollectionCopies.unspecified();

        // Then
        assertThat(unspecified).isEmpty();
    }

    @Test
    void specified_whenRead_namesEachSourceCollectionOnce() {
        // When
        List<String> collections = CollectionCopies.SPECIFIED.stream().map(CollectionCopy::collection).toList();

        // Then
        assertThat(collections).hasSize(40).doesNotHaveDuplicates();
        assertThat(CollectionCopies.SOURCE).hasSize(40).containsAll(collections);
    }
}
