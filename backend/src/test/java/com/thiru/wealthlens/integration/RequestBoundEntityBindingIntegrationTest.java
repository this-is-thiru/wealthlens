package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.AllowanceCatalogueEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.PerquisitePolicyEntity;
import com.thiru.wealthlens.taxplanning.policy.entity.TaxSlabPolicyEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * An entity bound from a request body keeps the state its field initialisers declare (W-205, D-409).
 *
 * <p>Jackson 3's constructor detector builds a Lombok class through its visible all-args constructor by default, even
 * when a public no-arg constructor exists, so an omitted field arrived {@code null} instead of initialised: a holding
 * lost its empty {@code sellTransactionIds} (a 500 at {@code PortfolioService:641}) and a published card lost its
 * {@code auditMetadata} (no author, because Spring Data's auditing fills an object rather than creating one).
 *
 * <p>The six types listed here are the request-bound ones. A new entity bound from a request body belongs in this
 * test; that is what keeps the next one from losing the same state silently.
 */
class RequestBoundEntityBindingIntegrationTest extends AbstractIntegrationTest {

    private static final String EMPTY = "{}";

    @Autowired
    private ObjectMapper mvcMapper;

    @Test
    void bind_whenARequestBoundEntityIsGivenAnEmptyBody_keepsItsInitialisedFields() {
        // Given — a body saying nothing about any field

        // When
        ChargeScheduleEntity schedule = mvcMapper.readValue(EMPTY, ChargeScheduleEntity.class);
        ChargeAccountEntity account = mvcMapper.readValue(EMPTY, ChargeAccountEntity.class);
        AssetEntity asset = mvcMapper.readValue(EMPTY, AssetEntity.class);
        TaxSlabPolicyEntity slabPolicy = mvcMapper.readValue(EMPTY, TaxSlabPolicyEntity.class);
        PerquisitePolicyEntity perquisitePolicy = mvcMapper.readValue(EMPTY, PerquisitePolicyEntity.class);
        AllowanceCatalogueEntity allowance = mvcMapper.readValue(EMPTY, AllowanceCatalogueEntity.class);

        // Then
        assertThat(schedule.getRules()).isNotNull();
        assertThat(schedule.getAuditMetadata()).isNotNull();
        assertThat(account.getBillingEvents()).isNotNull();
        assertThat(account.getAuditMetadata()).isNotNull();
        assertThat(asset.getBuyTransactionIds()).isNotNull();
        assertThat(asset.getSellTransactionIds()).isNotNull();
        assertThat(asset.getCorporateActions()).isNotNull();
        assertThat(asset.getAuditMetadata()).isNotNull();
        assertThat(slabPolicy.getAuditMetadata()).isNotNull();
        assertThat(perquisitePolicy.getAuditMetadata()).isNotNull();
        assertThat(allowance.getAuditMetadata()).isNotNull();
    }

    @Test
    void bind_whenTheBodySendsAuditMetadata_isIgnoredAndTheInitialiserSurvives() {
        // Given — the server owns who wrote a document; @Setter(NONE) already says a caller cannot
        String sent = "{\"auditMetadata\":{\"createdBy\":\"spoof\"}}";
        String blanked = "{\"auditMetadata\":null}";

        // When
        ChargeScheduleEntity spoofed = mvcMapper.readValue(sent, ChargeScheduleEntity.class);
        ChargeScheduleEntity nulled = mvcMapper.readValue(blanked, ChargeScheduleEntity.class);

        // Then
        assertThat(spoofed.getAuditMetadata()).isNotNull();
        assertThat(spoofed.getAuditMetadata().getCreatedBy()).isNull();
        assertThat(nulled.getAuditMetadata()).isNotNull();
    }

    @Test
    void bind_whenARulesPrimitiveIsOmitted_isStillRefused() {
        // Given — a rule's active/taxable/order decide what a card charges; the validator never checks them, so
        // the binding is the guard: an omitted one must not become a silent false or zero
        String rule = "{\"code\":\"STT\"}";

        // When / Then
        assertThatThrownBy(() -> mvcMapper.readValue(rule, ChargeRule.class))
                .hasMessageContaining("FAIL_ON_NULL_FOR_PRIMITIVES");
    }
}
