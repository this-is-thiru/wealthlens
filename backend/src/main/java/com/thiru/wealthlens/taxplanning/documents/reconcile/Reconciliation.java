package com.thiru.wealthlens.taxplanning.documents.reconcile;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedComparison;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.assembly.Assembly;
import com.thiru.wealthlens.taxplanning.documents.assembly.AssemblyMode;
import com.thiru.wealthlens.taxplanning.documents.ingest.ConfirmedCertificate;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A year's confirmed Form No. 130s against the engine's computation ({@code DR 05} M4.2; W-130a – c, D-755 – D-758). Each
 * figure the layout pairs with a node's output or a computation total is a line; a difference is explained by the closed
 * reasons, and the verdict follows D-755 C2a. With several employers, a table's Total (Part B's tax deducted) is summed over
 * their certificates against the bill, and the rows of the one Part C that covers the year, if one does, are compared as one
 * employer's (C1a). Only confirmed values are used (M4: "nothing is used until this exists").
 */
public final class Reconciliation {

    /** Rounding to the nearest ₹10 (RATE-ROUND-002; D-755 C5a). */
    private static final Money ROUNDING = Money.TEN;

    /** Why a MIXED year is not compared ({@code DR 08} T8-6's explanation; {@code DOC-F130-ANNEX-001}; W-132 C2a). */
    static final String MIXED_YEAR = "Some employers issued Part C and some did not, so the year cannot be compared with the computation:"
            + " the last employer's Part C should carry every earlier salary in row 1(e)";

    private Reconciliation() {
    }

    /** What one comparison needs besides its own figures. */
    private record Context(boolean regimeDiffers, boolean capitalGains, List<ReconciliationReason> employerErrors) {
    }

    /**
     * @param certificates the year's certificates as the user confirmed them, the report naming their documents
     * @param assembly how those certificates fit together (W-129)
     * @param law the year's law, for the regime a "No" in row A keeps
     */
    public static ReconciliationReport reconcile(List<ConfirmedCertificate> certificates, Assembly assembly, CertificateLayout layout,
            ComputedTax computed, SalaryCertificateLaw law) {
        List<String> documents = certificates.stream().map(ConfirmedCertificate::documentId).toList();
        List<DocumentConfirmation> confirmed = certificates.stream().map(ConfirmedCertificate::confirmation).toList();
        if (assembly.mode() == AssemblyMode.MIXED) {
            return new ReconciliationReport(computed.provenance().computationId(), documents, List.of(), Verdict.CANNOT_COMPARE, List.of(MIXED_YEAR));
        }
        Optional<Map<String, ExtractedValue>> partC = yearPartC(confirmed, assembly, layout);
        Context context = new Context(partC.map(values -> regimeDiffers(values, layout, computed, law)).orElse(false),
                !computed.workings().capitalGainsBuckets().isEmpty(), employerErrors(confirmed, assembly, layout));
        List<ReconciliationLine> lines = new ArrayList<>();
        List<ReconciliationReason> above = new ArrayList<>();
        for (PrintedComparison comparison : layout.checks().comparisons()) {
            boolean summed = confirmed.size() > 1 && isTableTotal(comparison, layout);
            Optional<Money> printed = summed ? summed(confirmed, comparison.printed())
                    : partC.flatMap(values -> amount(values.get(comparison.printed())));
            Optional<Money> produced = produced(comparison, computed);
            if (printed.isPresent() && produced.isPresent()) {
                Money difference = printed.get().minus(produced.get());
                Optional<ReconciliationReason> own = claimReason(comparison, difference);
                lines.add(new ReconciliationLine(comparison.printed(), printed.get(), produced.get(), difference,
                        reasons(comparison, difference, own, above, summed, context)));
                own.filter(reason -> !above.contains(reason)).ifPresent(above::add);
            }
        }
        return new ReconciliationReport(computed.provenance().computationId(), documents, lines, verdict(lines), explanations(lines));
    }

    /** Each reason's sentence once, in the order the lines first give it (W-132 C2a). */
    private static List<String> explanations(List<ReconciliationLine> lines) {
        return lines.stream().flatMap(line -> line.reasons().stream()).distinct().map(ReconciliationReason::sentence).toList();
    }

    /** The one Part C that covers the year: the only certificate's, or the consolidating employer's; none per employer. */
    private static Optional<Map<String, ExtractedValue>> yearPartC(List<DocumentConfirmation> confirmed, Assembly assembly,
            CertificateLayout layout) {
        if (confirmed.size() == 1) {
            return Optional.of(confirmed.get(0).acceptedFields());
        }
        if (assembly.mode() != AssemblyMode.LAST_EMPLOYER_CONSOLIDATED) {
            return Optional.empty();
        }
        String annexure = annexure(layout);
        return confirmed.stream().map(DocumentConfirmation::acceptedFields)
                .filter(values -> values.keySet().stream().anyMatch(path -> path.startsWith(annexure + "."))).findFirst();
    }

    /** The part holding the salary from other employers (Annexure-I): the part a Part C's rows sit in. */
    private static String annexure(CertificateLayout layout) {
        String row = factPath(layout, "SALARY_FROM_OTHER_EMPLOYERS").orElseThrow();
        return row.substring(0, row.lastIndexOf('.'));
    }

    private static Optional<String> factPath(CertificateLayout layout, String fact) {
        return layout.lines().stream().filter(line -> line.fact().filter(fact::equals).isPresent()).map(CertificateLayout.PrintedLine::path)
                .findFirst();
    }

    private static boolean isTableTotal(PrintedComparison comparison, CertificateLayout layout) {
        return layout.tables().stream().anyMatch(table -> comparison.printed().startsWith(table.path() + ".total."));
    }

    private static Optional<Money> summed(List<DocumentConfirmation> confirmed, String path) {
        List<Money> printed = confirmed.stream().map(confirmation -> amount(confirmation.acceptedFields().get(path)))
                .flatMap(Optional::stream).toList();
        return printed.isEmpty() ? Optional.empty() : Optional.of(printed.stream().reduce(Money.ZERO, Money::plus));
    }

    /**
     * The employers' errors across the year (C3a): a standard deduction each granted (W-129's notice), and an earlier salary
     * an employer was not told of — the consolidating Part C's nil 1(e) (W-129's notice) or, per employer, any after the
     * first by period printing 1(e) nil.
     */
    private static List<ReconciliationReason> employerErrors(List<DocumentConfirmation> confirmed, Assembly assembly,
            CertificateLayout layout) {
        List<ReconciliationReason> errors = new ArrayList<>();
        if (assembly.notices().stream().anyMatch(notice -> notice.relatedRules().contains("SAL-STDDED-001"))) {
            errors.add(ReconciliationReason.STANDARD_DEDUCTION_TWICE);
        }
        boolean consolidatedNil = assembly.notices().stream().anyMatch(notice -> notice.relatedRules().contains("TDS-SAL-12B-001"));
        if (consolidatedNil || assembly.mode() == AssemblyMode.PER_EMPLOYER && laterSalaryUndeclared(confirmed, layout)) {
            errors.add(ReconciliationReason.PREVIOUS_SALARY_UNDECLARED);
        }
        return errors;
    }

    private static boolean laterSalaryUndeclared(List<DocumentConfirmation> confirmed, CertificateLayout layout) {
        if (confirmed.size() < 2) {
            return false;
        }
        String period = factPath(layout, "EMPLOYMENT_PERIOD").orElseThrow();
        String otherSalary = factPath(layout, "SALARY_FROM_OTHER_EMPLOYERS").orElseThrow();
        List<Map<String, ExtractedValue>> byPeriod = confirmed.stream().map(DocumentConfirmation::acceptedFields)
                .filter(values -> values.get(period) instanceof ExtractedValue.PeriodRead)
                .sorted(Comparator.comparing(values -> ((ExtractedValue.PeriodRead) values.get(period)).from())).toList();
        return byPeriod.stream().skip(1).anyMatch(values -> !amount(values.get(otherSalary)).filter(salary -> salary.isGreaterThan(Money.ZERO))
                .isPresent());
    }

    /** A node's last output in the trace, or a computation total; none where the computation did not reach the node. */
    private static Optional<Money> produced(PrintedComparison comparison, ComputedTax computed) {
        if (comparison.total().isPresent()) {
            return Optional.of(computed.figures().total(comparison.total().get()));
        }
        String node = comparison.node().orElseThrow();
        return computed.workings().trace().stream().filter(step -> step.node().equals(node) && step.out().isPresent())
                .reduce((first, second) -> second).flatMap(ComputedTax.Step::out);
    }

    /** Row A against the computation's regime: "No" keeps the year's default, "Yes" leaves it. */
    private static boolean regimeDiffers(Map<String, ExtractedValue> accepted, CertificateLayout layout, ComputedTax computed,
            SalaryCertificateLaw law) {
        return factPath(layout, "REGIME_OPT_OUT").map(accepted::get).filter(ExtractedValue.FlagRead.class::isInstance)
                .map(read -> ((ExtractedValue.FlagRead) read).value() == computed.provenance().regime().equals(law.defaultRegime().value()))
                .orElse(false);
    }

    /**
     * A claim's own reason, by which way the certificate differs (W-130b C2a): above the computation a claim declared but not
     * proven, below it one never declared; other income reported or not (W-130d). A later line's difference with no reason of
     * its own is the consequence of every such reason above it (W-130b C3a, W-130d C3a), so the comparisons are listed in the
     * form's printed order.
     */
    private static Optional<ReconciliationReason> claimReason(PrintedComparison comparison, Money difference) {
        if (!isBeyondRounding(difference)) {
            return Optional.empty();
        }
        return (difference.isGreaterThan(Money.ZERO) ? comparison.whenHigher() : comparison.whenLower()).map(ReconciliationReason::valueOf);
    }

    /** A summed Total short of the bill takes the employers' errors (W-130c C3a); otherwise W-130a/b's reasons. */
    private static List<ReconciliationReason> reasons(PrintedComparison comparison, Money difference, Optional<ReconciliationReason> own,
            List<ReconciliationReason> above, boolean summed, Context context) {
        if (summed && isBeyondRounding(difference) && !context.employerErrors().isEmpty()) {
            return context.employerErrors();
        }
        if (own.isPresent()) {
            return List.of(own.get());
        }
        if (difference.compareTo(Money.ZERO) == 0) {
            return comparison.reason().map(ReconciliationReason::valueOf).stream().toList();
        }
        if (!isBeyondRounding(difference)) {
            return List.of(ReconciliationReason.ROUNDING);
        }
        if (context.regimeDiffers()) {
            return List.of(ReconciliationReason.REGIME_DIFFERS);
        }
        if (context.capitalGains()) {
            return List.of(ReconciliationReason.CAPITAL_GAINS);
        }
        return above;
    }

    private static boolean isBeyondRounding(Money difference) {
        return Money.max(difference, Money.ZERO.minus(difference)).isGreaterThan(ROUNDING);
    }

    /** D-755 C2a: all within ₹10 matched; every difference expected minor; an employer's error or nothing material. */
    private static Verdict verdict(List<ReconciliationLine> lines) {
        List<ReconciliationLine> differing = lines.stream().filter(line -> isBeyondRounding(line.difference())).toList();
        if (differing.isEmpty()) {
            return Verdict.MATCHED;
        }
        boolean expected = differing.stream().allMatch(line -> !line.reasons().isEmpty()
                && line.reasons().stream().allMatch(reason -> reason.kind() == ReconciliationReason.Kind.EXPECTED));
        return expected ? Verdict.MINOR_DIFFERENCE : Verdict.MATERIAL_DIFFERENCE;
    }

    private static Optional<Money> amount(ExtractedValue value) {
        return Optional.ofNullable(value).filter(ExtractedValue.MoneyRead.class::isInstance).map(read -> ((ExtractedValue.MoneyRead) read).amount());
    }
}
