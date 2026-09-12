package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.entity.ChargeCatalogueEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeCatalogueRepository;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The registry of charge codes a rate card is allowed to name.
 *
 * <p>Read-only and small, but it is what makes {@code GET /charge-catalogue} answerable without a
 * controller reaching for a repository. Its one judgement is that the listing offers active codes
 * only: a retired code still has to resolve for the historical rows that carry it, but offering it
 * to somebody authoring a new rate card would invite a card nobody can explain later.
 */
@ExtendWith(MockitoExtension.class)
class ChargeCatalogueServiceTest {

    @Mock
    private ChargeCatalogueRepository chargeCatalogueRepository;

    @InjectMocks
    private ChargeCatalogueService chargeCatalogueService;

    @Test
    void findActive_returnsOnlyActiveCodes() {
        // Given
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE))
                .thenReturn(List.of(entry("STT"), entry("BROKERAGE")));

        // When
        List<ChargeCatalogueEntity> catalogue = chargeCatalogueService.findActive();

        // Then
        assertThat(catalogue).extracting(ChargeCatalogueEntity::getCode)
                .containsExactly("BROKERAGE", "STT");
    }

    @Test
    void findActive_sortsByCodeSoTheListingIsStable() {
        // Given — Mongo returns insertion order, which changes when the seeder is re-run.
        // A listing that reshuffles between calls is one nobody can diff.
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE))
                .thenReturn(List.of(entry("GST"), entry("DP"), entry("STT"), entry("BROKERAGE")));

        // When / Then
        assertThat(chargeCatalogueService.findActive()).extracting(ChargeCatalogueEntity::getCode)
                .containsExactly("BROKERAGE", "DP", "GST", "STT");
    }

    @Test
    void findActive_whenNothingIsSeeded_isEmptyRatherThanNull() {
        // Given — the seeder runs at startup, so an empty catalogue means it failed
        when(chargeCatalogueRepository.findByStatus(EntityStatus.ACTIVE)).thenReturn(List.of());

        // When / Then
        assertThat(chargeCatalogueService.findActive()).isEmpty();
    }

    private static ChargeCatalogueEntity entry(String code) {
        ChargeCatalogueEntity entry = new ChargeCatalogueEntity();
        entry.setCode(code);
        entry.setDisplayName(code);
        entry.setCategory(ChargeCategory.BROKERAGE);
        entry.setStatus(EntityStatus.ACTIVE);
        return entry;
    }
}
