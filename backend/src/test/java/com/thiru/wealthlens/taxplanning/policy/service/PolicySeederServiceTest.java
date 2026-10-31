package com.thiru.wealthlens.taxplanning.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.taxplanning.policy.dto.PolicySeedReport;
import com.thiru.wealthlens.taxplanning.policy.dto.PolicySeedReport.Part;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceLimitEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceCatalogueRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.AllowanceLimitRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.PerquisitePolicyRepository;
import com.thiru.wealthlens.taxplanning.policy.repository.TaxSlabPolicyRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The tax-policy seed as an operation (W-088u8a): dry run, per-document idempotence, the shipped files' sizes — slab 1
 * per regime, perquisite 1, catalogue 39, limits 14, counted from {@code data/tax-policies/} (D-279).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicySeederServiceTest {

    @Mock private TaxSlabPolicyRepository slabRepo;
    @Mock private PerquisitePolicyRepository perquisiteRepo;
    @Mock private AllowanceCatalogueRepository allowanceRepo;
    @Mock private AllowanceLimitRepository limitRepo;

    @InjectMocks private PolicySeederService seeder;

    private void nothingOnFile() {
        when(slabRepo.findByTaxYearAndRegimeTypeAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(perquisiteRepo.findByTaxYearAndStatus(any(), any())).thenReturn(Optional.empty());
        when(allowanceRepo.existsByCodeAndTaxYearAndRegimeType(any(), any(), any())).thenReturn(false);
        when(limitRepo.existsByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerType(any(), any(), any(), any()))
                .thenReturn(false);
    }

    @Test
    void seed_whenADryRunOnAnEmptyDatabase_reportsEveryDocumentAndSavesNothing() {
        // Given
        nothingOnFile();

        // When
        PolicySeedReport report = seeder.seed(true, "admin@example.com");

        // Then
        assertThat(report.dryRun()).isTrue();
        assertThat(report.parts()).extracting(Part::name, Part::newDocuments, Part::alreadyPresent).containsExactly(
                org.assertj.core.groups.Tuple.tuple("SLAB_NEW_REGIME", 1, 0),
                org.assertj.core.groups.Tuple.tuple("SLAB_OLD_REGIME", 1, 0),
                org.assertj.core.groups.Tuple.tuple("PERQUISITE", 1, 0),
                org.assertj.core.groups.Tuple.tuple("ALLOWANCE_CATALOGUE", 39, 0),
                org.assertj.core.groups.Tuple.tuple("ALLOWANCE_LIMITS", 14, 0));
        verify(slabRepo, never()).save(any());
        verify(perquisiteRepo, never()).save(any());
        verify(allowanceRepo, never()).save(any());
        verify(limitRepo, never()).save(any());
    }

    @Test
    void seed_whenWritten_savesEveryDocument() {
        // Given
        nothingOnFile();

        // When
        seeder.seed(false, "admin@example.com");

        // Then
        verify(slabRepo, times(2)).save(any(TaxSlabPolicyEntity.class));
        verify(perquisiteRepo, times(1)).save(any(PerquisitePolicyEntity.class));
        verify(allowanceRepo, times(39)).save(any(AllowanceCatalogueEntity.class));
        verify(limitRepo, times(14)).save(any(AllowanceLimitEntity.class));
    }

    @Test
    void seed_whenEverythingIsOnFile_writesNothing() {
        // Given
        when(slabRepo.findByTaxYearAndRegimeTypeAndStatus(any(), any(), any()))
                .thenReturn(Optional.of(new TaxSlabPolicyEntity()));
        when(perquisiteRepo.findByTaxYearAndStatus(any(), any())).thenReturn(Optional.of(new PerquisitePolicyEntity()));
        when(allowanceRepo.existsByCodeAndTaxYearAndRegimeType(any(), any(), any())).thenReturn(true);
        when(limitRepo.existsByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerType(any(), any(), any(), any()))
                .thenReturn(true);

        // When
        PolicySeedReport report = seeder.seed(false, "admin@example.com");

        // Then
        assertThat(report.parts()).extracting(Part::newDocuments).containsOnly(0);
        assertThat(report.parts()).extracting(Part::alreadyPresent).containsExactly(1, 1, 1, 39, 14);
        verify(allowanceRepo, never()).save(any());
        verify(limitRepo, never()).save(any());
    }

    @Test
    void seed_whenTheCatalogueWasPartlySeeded_seedsOnlyTheRest() {
        // Given — a run that failed after the first 20 of the 39 catalogue entries
        nothingOnFile();
        AtomicInteger asked = new AtomicInteger();
        when(allowanceRepo.existsByCodeAndTaxYearAndRegimeType(any(), any(), any()))
                .thenAnswer(invocation -> asked.incrementAndGet() <= 20);

        // When
        PolicySeedReport report = seeder.seed(false, "admin@example.com");

        // Then — 39 − 20 = 19 seeded now
        assertThat(report.parts().get(3)).isEqualTo(new Part("ALLOWANCE_CATALOGUE",
                "data/tax-policies/allowance-catalogue-2025-26.json", 19, 20));
        verify(allowanceRepo, times(19)).save(any(AllowanceCatalogueEntity.class));
    }

    @Test
    void read_whenAFileCarriesAPropertyThePolicyDoesNot_failsNamingTheFileAndTheProperty() {
        // Given — a policy file whose field is misspelt; ignoring it would seed the policy with the value unset
        // When / Then
        assertThatThrownBy(() -> seeder.read("tax-policies-fixture/misspelt-slab.json", new TaxSlabPolicyEntity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("misspelt-slab.json")
                .hasMessageContaining("standardDeducton");
    }
}
