package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.input.LoanUse;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.QualifyingPayment;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.rules.catalogue.EvidenceRecord;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist.Document;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist.State;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklistService.OnFile;
import com.thiru.wealthlens.taxplanning.workspace.ChapterViiiEvidence;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The documentation checklist on the engine alone (W-120): {@code DR 22} T22-10 and T22-11, {@code DR 12} T12-13, a home
 * loan's two documents and a document the product keeps no record of — each expected value worked by hand in its case.
 * TY 2026-27, old regime.
 */
class DraftDocumentChecklistServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftDocumentChecklistService SERVICE =
            new DraftDocumentChecklistService(null, null, CALCULATOR, null, null);

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);

    private static final ComponentCode TUITION_FEES = new ComponentCode("TUITION_FEES");

    private static final OnFile NOTHING_ON_FILE = new OnFile(false, false);

    private static EngineInputs inputs(EnteredFacts entered) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(TY_2026_27, new RegimeCode("FIRST_SCHEDULE_RATES"), plain.temporal(), entered,
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    private static Employment pay(String basic, String hra) {
        return new Employment(List.of(new SalaryComponent(ComponentCodes.BASIC, Money.of(basic)),
                new SalaryComponent(ComponentCodes.HRA, Money.of(hra))), false, EmployerKind.OTHER, false,
                Optional.empty());
    }

    /** Basic 3,00,000 and HRA 1,00,000; a whole year's tenancy in Bengaluru at the given annual rent. */
    private static EnteredFacts renting(String annualRent) {
        return EnteredFacts.none().withEmployments(List.of(pay("300000", "100000"))).withTenancies(List.of(new Tenancy(
                DateRange.between(TY_2026_27.startsOn(), TY_2026_27.endsOn()), "BENGALURU", false, Money.of(annualRent))));
    }

    private static EvidenceHeld receipted(int months) {
        return new EvidenceHeld(List.of(months), List.of(), Map.of(), ChapterViiiEvidence.NONE);
    }

    private static List<Document> documentsOf(List<DraftDocumentChecklist.Claim> claims, String rule) {
        return claims.stream().filter(claim -> claim.rule().equals(rule)).findFirst().orElseThrow().documents();
    }

    private static Document named(List<Document> documents, String name) {
        return documents.stream().filter(document -> document.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void claims_whenRentIsNotAboveTheParticularsThreshold_doesNotAskForTheLandlord() {
        // Given — T22-10: 8,000 × 12 = 96,000, not above r.205(2) Sl. 1's 1,00,000; HRA is 96,000 − 30,000 (a tenth of
        // basic 3,00,000) = 66,000, so the claim is made; all twelve months receipted
        List<DraftDocumentChecklist.Claim> claims = SERVICE.claims(inputs(renting("96000")), receipted(12), NOTHING_ON_FILE);

        // When
        List<Document> rent = documentsOf(claims, "SAL-HOUSE-RENT-ALLOWANCE");

        // Then
        assertThat(named(rent, "Rent receipts").state()).isEqualTo(State.HELD);
        assertThat(named(rent, "Rent receipts").says()).isEqualTo("Fully evidenced.");
        Document landlord = named(rent, "Your landlord's particulars");
        assertThat(landlord.state()).isEqualTo(State.NOT_ASKED);
        assertThat(landlord.says()).startsWith("Not needed this year");
    }

    @Test
    void claims_whenRentIsAboveTheThresholdAndFourMonthsAreNotReceipted_namesBothAndWhomToAsk() {
        // Given — T22-11: 10,000 × 12 = 1,20,000, above 1,00,000; eight months receipted, so 12 − 8 = 4 outstanding
        List<DraftDocumentChecklist.Claim> claims = SERVICE.claims(inputs(renting("120000")), receipted(8), NOTHING_ON_FILE);

        // When
        List<Document> rent = documentsOf(claims, "SAL-HOUSE-RENT-ALLOWANCE");

        // Then
        Document receipts = named(rent, "Rent receipts");
        assertThat(receipts.state()).isEqualTo(State.NOT_HELD);
        assertThat(receipts.outstanding()).hasValueSatisfying(left -> assertThat(left.months()).contains(4L));
        assertThat(receipts.says()).isEqualTo("Rent receipts for the 4 months not yet receipted would evidence it.");
        Document landlord = named(rent, "Your landlord's particulars");
        assertThat(landlord.state()).isEqualTo(State.NOT_HELD);
        assertThat(landlord.from()).isEqualTo("LANDLORD");
        assertThat(landlord.askFor()).hasValueSatisfying(ask -> assertThat(ask).contains("PAN"));
        assertThat(landlord.says()).isEqualTo("Your landlord's name, address and PAN are not all on file for this draft.");
    }

    @Test
    void claims_whenTheLandlordIsOnFile_holdsTheirParticulars() {
        // When — the same 1,20,000 rent, the landlord's name, address and PAN on file
        List<DraftDocumentChecklist.Claim> claims =
                SERVICE.claims(inputs(renting("120000")), receipted(12), new OnFile(true, false));

        // Then
        assertThat(named(documentsOf(claims, "SAL-HOUSE-RENT-ALLOWANCE"), "Your landlord's particulars").state())
                .isEqualTo(State.HELD);
    }

    @Test
    void claims_whenAPaymentIsPartlyEvidenced_showsTheRestOutstandingFromTheInstitution() {
        // Given — T12-13: 1,50,000 paid, 40,000 evidenced; 1,50,000 − 40,000 = 1,10,000 outstanding
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(pay("1000000", "0")))
                .withQualifyingPayments(List.of(new QualifyingPayment(TUITION_FEES, Money.of("150000"))));
        EvidenceHeld held = new EvidenceHeld(List.of(), List.of(), Map.of(TUITION_FEES, Money.of("40000")),
                ChapterViiiEvidence.NONE);

        // When
        Document receipts = named(documentsOf(SERVICE.claims(inputs(entered), held, NOTHING_ON_FILE), "DED-SCHEDULE-XV"),
                "Payment receipts and statements");

        // Then
        assertThat(receipts.state()).isEqualTo(State.NOT_HELD);
        assertThat(receipts.from()).isEqualTo("INSTITUTION");
        assertThat(receipts.outstanding()).hasValueSatisfying(left -> assertThat(left.amount())
                .hasValueSatisfying(amount -> assertThat(amount).isEqualTo(Money.of("110000"))));
        assertThat(receipts.says()).isEqualTo("Evidence of the ₹1,10,000 paid but not yet evidenced would evidence it.");
    }

    @Test
    void claims_whenAHomeLoansCertificateIsHeldButTheLenderIsNotOnFile_holdsOneAndNotTheOther() {
        // Given — interest 1,50,000 on a loan for the home lived in, the lender's certificate held
        HousingLoan loan = new HousingLoan(LoanUse.ACQUISITION_OR_CONSTRUCTION, LocalDate.of(2022, 5, 1),
                Money.of("150000"), Money.ZERO, Money.ZERO, true, false);
        HouseProperty home = new HouseProperty(HouseOccupancy.OWN_RESIDENCE, Money.of("480000"), Money.ZERO,
                Money.ZERO, false, Money.ZERO, Rate.percent("100"), Money.ZERO, Optional.of(LocalDate.of(2024, 6, 1)),
                List.of(loan));
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(pay("1000000", "0")))
                .withHouseProperties(List.of(home));

        // When
        List<Document> interest = documentsOf(SERVICE.claims(inputs(entered), EvidenceHeld.NONE, NOTHING_ON_FILE),
                "HP-OWN-RESIDENCE-INTEREST-CAP");

        // Then
        assertThat(named(interest, "Lender's certificate of interest").state()).isEqualTo(State.HELD);
        Document particulars = named(interest, "Your lender's particulars");
        assertThat(particulars.state()).isEqualTo(State.NOT_HELD);
        assertThat(particulars.from()).isEqualTo("LENDER");
        assertThat(particulars.says()).isEqualTo("Add your lender's name, address and PAN to your profile.");
    }

    @Test
    void claims_whenTheProductKeepsNoRecordOfADocument_saysSoWithoutCallingItHeld() {
        // Given — savings interest of 8,000 is deducted (DED-SAVINGS-INTEREST); its statement is kept nowhere here
        EnteredFacts entered = EnteredFacts.none().withEmployments(List.of(pay("1000000", "0")))
                .withOtherSourceItems(List.of(new OtherSourceItem(OtherSourceKind.SAVINGS_DEPOSIT_INTEREST,
                        Money.of("8000"), Money.ZERO, Money.ZERO, Money.ZERO, false, Optional.empty())));

        // When
        Document statement = named(documentsOf(SERVICE.claims(inputs(entered), EvidenceHeld.NONE, NOTHING_ON_FILE),
                "DED-SAVINGS-INTEREST"), "Interest statement");

        // Then
        assertThat(statement.state()).isEqualTo(State.NOT_RECORDED);
        assertThat(statement.outstanding()).isEmpty();
        assertThat(statement.says()).isEqualTo("WealthLens keeps no record of this document; check that you hold it.");
    }

    @Test
    void claims_whenNoEvidenceIsRecorded_holdsNothingTheDraftMeasures() {
        // Given — a draft stored before W-119b reads as nothing held: 1,20,000 rent and no months receipted
        List<DraftDocumentChecklist.Claim> claims =
                SERVICE.claims(inputs(renting("120000")), EvidenceHeld.NONE, NOTHING_ON_FILE);

        // When
        Document receipts = named(documentsOf(claims, "SAL-HOUSE-RENT-ALLOWANCE"), "Rent receipts");

        // Then — all 12 months outstanding
        assertThat(receipts.state()).isEqualTo(State.NOT_HELD);
        assertThat(receipts.outstanding()).hasValueSatisfying(left -> assertThat(left.months()).contains(12L));
    }

    @Test
    void claims_whenEveryRecordTheCatalogueCanName_isReadByTheChecklist() {
        // Given — the catalogue's type lives in the engine, so the checklist reads its names; a name it does not read
        // would be refused at the first claim naming it
        EnteredFacts entered = renting("120000");

        // When / Then — every value reaches a case, none the refusal
        DraftDocumentChecklistService.Reading reading = SERVICE.reading(inputs(entered), EvidenceHeld.NONE, NOTHING_ON_FILE);
        for (EvidenceRecord record : EvidenceRecord.values()) {
            assertThat(reading.of(record.name(), "SAL-HOUSE-RENT-ALLOWANCE").state()).as(record.name()).isNotNull();
        }
        assertThatThrownBy(() -> reading.of("RECEIPT_BOX", "SAL-HOUSE-RENT-ALLOWANCE"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RECEIPT_BOX");
    }
}
