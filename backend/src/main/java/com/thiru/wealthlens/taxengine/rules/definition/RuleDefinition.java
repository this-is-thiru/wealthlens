package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * What a rule says an amount <em>is</em>, as one of a closed set of shapes (D-091).
 *
 * <p>The Acts define as well as limit: "salary includes …" (S2 s.16) sets what gross salary is made of, and
 * is none of a limit, a relief, a rounding or a condition. Holding it as a shape on a rule row gives it what
 * only a row has — a version per year, provenance, a line in the trace, and a rule for components to feed —
 * while its content stays data. A new kind of definition is a new class and a new permit.
 */
public sealed interface RuleDefinition permits HeadTotal, SalaryBase, JourneysPerCalendarBlock,
        DisabledTransportLimit, CommutedPensionShare, IndexByYear, ExcludedAssets, DayWindow,
        PremiumShareBands, TaxYearSpan, SanctionedLoanLimit, CashPaymentLimit, OwnResidenceHouses,
        OwnResidenceInterestLimit, DisallowanceBasis, ParticularsThreshold {
}
