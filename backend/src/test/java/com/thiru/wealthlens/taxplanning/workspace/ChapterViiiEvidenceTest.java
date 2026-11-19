package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Donation;
import com.thiru.wealthlens.taxengine.input.DonationClause;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HealthPayment;
import com.thiru.wealthlens.taxengine.input.HealthPaymentKind;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Evidence held for the other Chapter VIII entries (W-119b1): matched by position, never beyond what an entry states. */
class ChapterViiiEvidenceTest {

    /** A ₹25,000 premium and a ₹10,000 donation to a named fund. */
    private static final EnteredFacts ENTERED = EnteredFacts.none()
            .withHealthPayments(List.of(new HealthPayment(HealthPaymentKind.INSURANCE_PREMIUM, true, List.of(),
                    Money.of("25000"), false, 1)))
            .withDonations(List.of(new Donation(DonationClause.NAMED_FUND, Money.of("10000"), false, false)));

    private static ChapterViiiEvidence held(List<Money> health, List<Money> donations, Optional<Money> agniveer) {
        return new ChapterViiiEvidence(health, donations, List.of(), List.of(), List.of(), List.of(), agniveer);
    }

    @Test
    void read_whenADraftWasStoredBeforeIt_holdsNothing() {
        // When — evidence as W-119b stored it, with no chapterViii
        DraftInputs read = DraftInputsMapper.read("{\"evidence\": {\"receiptedMonths\": []}}");

        // Then
        assertThat(read.evidence().chapterViii()).isEqualTo(ChapterViiiEvidence.NONE);
    }

    @Test
    void requireFits_whenEachAmountIsWithinItsEntry_accepts() {
        // When / Then — half the premium, all the donation
        assertThatCode(() -> held(List.of(Money.of("12500")), List.of(Money.of("10000")), Optional.empty())
                .requireFits(ENTERED)).doesNotThrowAnyException();
    }

    @Test
    void requireFits_whenAListIsNotAsLongAsItsEntries_isRefusedNamingBoth() {
        // When / Then
        assertThatThrownBy(() -> held(List.of(), List.of(Money.of("5000"), Money.of("5000")), Optional.empty())
                .requireFits(ENTERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/chapterViii/donations holds 2 but /entered/donations holds 1");
    }

    @Test
    void requireFits_whenAnAmountIsBeyondItsEntry_isRefusedNamingTheBound() {
        // When / Then — ₹30,000 evidenced for a ₹25,000 premium
        assertThatThrownBy(() -> held(List.of(Money.of("30000")), List.of(), Optional.empty()).requireFits(ENTERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/evidence/chapterViii/healthPayments/0 must be from nil to");
    }

    @Test
    void requireFits_whenTheAgniveerFundIsNotEntered_isRefused() {
        // When / Then
        assertThatThrownBy(() -> held(List.of(), List.of(), Optional.of(Money.of("1000"))).requireFits(ENTERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/entered/agniveerCorpusFund does not have");
    }
}
