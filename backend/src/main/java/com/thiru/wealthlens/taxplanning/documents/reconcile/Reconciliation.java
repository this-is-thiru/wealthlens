package com.thiru.wealthlens.taxplanning.documents.reconcile;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedComparison;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.assembly.AssemblyMode;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One employer's confirmed Form No. 130 against the engine's computation ({@code DR 05} M4.2; W-130a, D-755, D-756). Each
 * figure the layout pairs with a node's output or a computation total is a line; a difference is explained by the closed
 * reasons, and the verdict follows D-755 C2a. Only confirmed values are used (M4: "nothing is used until this exists").
 */
public final class Reconciliation {

    /** Rounding to the nearest ₹10 (RATE-ROUND-002; D-755 C5a). */
    private static final Money ROUNDING = Money.TEN;

    private Reconciliation() {
    }

    /**
     * @param confirmed the user's confirmations of the year's certificates
     * @param assembly how those certificates fit together (W-129)
     * @param law the year's law, for the regime a "No" in row A keeps
     * @throws IllegalArgumentException for several employers' certificates that are not mixed: their reasons are W-130c's
     */
    public static ReconciliationReport reconcile(List<DocumentConfirmation> confirmed, Assembly assembly, CertificateLayout layout,
            ComputedTax computed, SalaryCertificateLaw law) {
        List<String> documents = confirmed.stream().map(DocumentConfirmation::attemptId).toList();
        if (assembly.mode() == AssemblyMode.MIXED) {
            return new ReconciliationReport(computed.computationId(), documents, List.of(), Verdict.CANNOT_COMPARE);
        }
        if (confirmed.size() != 1) {
            throw new IllegalArgumentException("Several employers' certificates are reconciled from W-130c; " + confirmed.size() + " given");
        }
        Map<String, ExtractedValue> accepted = confirmed.get(0).acceptedFields();
        boolean regimeDiffers = regimeDiffers(accepted, layout, computed, law);
        List<ReconciliationLine> lines = new ArrayList<>();
        for (PrintedComparison comparison : layout.comparisons()) {
            Optional<Money> printed = amount(accepted.get(comparison.printed()));
            Optional<Money> produced = produced(comparison, computed);
            if (printed.isPresent() && produced.isPresent()) {
                Money difference = printed.get().minus(produced.get());
                lines.add(new ReconciliationLine(comparison.printed(), printed.get(), produced.get(), difference,
                        reason(comparison, difference, regimeDiffers, !computed.capitalGainsBuckets().isEmpty())));
            }
        }
        return new ReconciliationReport(computed.computationId(), documents, lines, verdict(lines));
    }

    /** A node's last output in the trace, or a computation total; none where the computation did not reach the node. */
    private static Optional<Money> produced(PrintedComparison comparison, ComputedTax computed) {
        if (comparison.total().isPresent()) {
            return Optional.of(computed.total(comparison.total().get()));
        }
        String node = comparison.node().orElseThrow();
        return computed.trace().stream().filter(step -> step.node().equals(node) && step.out().isPresent())
                .reduce((first, second) -> second).flatMap(ComputedTax.Step::out);
    }

    /** Row A against the computation's regime: "No" keeps the year's default, "Yes" leaves it. */
    private static boolean regimeDiffers(Map<String, ExtractedValue> accepted, CertificateLayout layout, ComputedTax computed,
            SalaryCertificateLaw law) {
        return layout.lines().stream().filter(line -> line.fact().filter("REGIME_OPT_OUT"::equals).isPresent()).findFirst()
                .map(line -> accepted.get(line.path())).filter(ExtractedValue.FlagRead.class::isInstance)
                .map(read -> ((ExtractedValue.FlagRead) read).value() == computed.regime().equals(law.defaultRegime().value()))
                .orElse(false);
    }

    private static Optional<ReconciliationReason> reason(PrintedComparison comparison, Money difference, boolean regimeDiffers,
            boolean capitalGains) {
        Money apart = Money.max(difference, Money.ZERO.minus(difference));
        if (apart.compareTo(Money.ZERO) == 0) {
            return comparison.reason().map(ReconciliationReason::valueOf);
        }
        if (!apart.isGreaterThan(ROUNDING)) {
            return Optional.of(ReconciliationReason.ROUNDING);
        }
        if (regimeDiffers) {
            return Optional.of(ReconciliationReason.REGIME_DIFFERS);
        }
        return capitalGains ? Optional.of(ReconciliationReason.CAPITAL_GAINS) : Optional.empty();
    }

    /** D-755 C2a: all within ₹10 matched; every difference expected minor; an employer's error or nothing material. */
    private static Verdict verdict(List<ReconciliationLine> lines) {
        List<ReconciliationLine> differing = lines.stream()
                .filter(line -> Money.max(line.difference(), Money.ZERO.minus(line.difference())).isGreaterThan(ROUNDING)).toList();
        if (differing.isEmpty()) {
            return Verdict.MATCHED;
        }
        boolean expected = differing.stream().allMatch(line -> line.reason()
                .filter(reason -> reason.kind() == ReconciliationReason.Kind.EXPECTED).isPresent());
        return expected ? Verdict.MINOR_DIFFERENCE : Verdict.MATERIAL_DIFFERENCE;
    }

    private static Optional<Money> amount(ExtractedValue value) {
        return Optional.ofNullable(value).filter(ExtractedValue.MoneyRead.class::isInstance).map(read -> ((ExtractedValue.MoneyRead) read).amount());
    }
}
