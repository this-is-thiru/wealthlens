package com.thiru.wealthlens.taxplanning.documents.assembly;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A year's Form No. 130s read together ({@code DR 06} G5.3; W-129, D-754). Each certificate is found by the facts its layout
 * names, never by path: Part A's TAN and period, Annexure-I's row A, 1(e) and 4(b). It reports how the certificates carry
 * Part C, a gap or an overlap between employers' periods, and what will be owed at filing: a previous salary the last
 * employer left out, or a standard deduction every employer granted.
 */
public final class EmployerAssembly {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private EmployerAssembly() {
    }

    /** One certificate's facts, as read. */
    private record Certificate(String documentId, Optional<String> tan, Optional<ExtractedValue.PeriodRead> period, boolean hasPartC,
            Optional<Boolean> optsOut, Optional<Money> otherSalary, Optional<Money> standardDeduction) {
    }

    /**
     * @param attempts the latest reading of each certificate of one tax year
     * @param layout the year's Form No. 130 layout, whose lines carry the facts
     * @param law the year's law, for one year's standard deduction
     */
    public static Assembly assemble(List<ParseAttempt> attempts, CertificateLayout layout, SalaryCertificateLaw law) {
        Map<String, String> paths = layout.lines().stream().filter(line -> line.fact().isPresent())
                .collect(Collectors.toMap(line -> line.fact().orElseThrow(), CertificateLayout.PrintedLine::path, (first, second) -> first));
        String annexure = paths.get("SALARY_FROM_OTHER_EMPLOYERS").substring(0, paths.get("SALARY_FROM_OTHER_EMPLOYERS").lastIndexOf('.'));
        List<Certificate> year = attempts.stream().map(attempt -> certificate(attempt, paths, annexure)).toList();
        long partCs = year.stream().filter(Certificate::hasPartC).count();
        AssemblyMode mode = partCs == 1 && year.size() > 1 ? AssemblyMode.LAST_EMPLOYER_CONSOLIDATED
                : partCs == year.size() ? AssemblyMode.PER_EMPLOYER : AssemblyMode.MIXED;
        List<AssemblyNotice> notices = new ArrayList<>(periods(year));
        if (mode == AssemblyMode.LAST_EMPLOYER_CONSOLIDATED) {
            previousSalary(year).ifPresent(notices::add);
        } else if (mode == AssemblyMode.PER_EMPLOYER && year.size() > 1) {
            standardDeduction(year, law).ifPresent(notices::add);
        }
        return new Assembly(mode, notices);
    }

    private static Certificate certificate(ParseAttempt attempt, Map<String, String> paths, String annexure) {
        Map<String, ExtractedField> byPath = attempt.extractedFields().stream()
                .collect(Collectors.toMap(ExtractedField::fieldPath, field -> field));
        java.util.function.Function<String, Optional<ExtractedValue>> value = fact -> Optional.ofNullable(paths.get(fact))
                .map(byPath::get).flatMap(ExtractedField::interpretedValue);
        return new Certificate(attempt.documentId(),
                value.apply("EMPLOYER_TAN").filter(ExtractedValue.TextRead.class::isInstance).map(read -> ((ExtractedValue.TextRead) read).text()),
                value.apply("EMPLOYMENT_PERIOD").filter(ExtractedValue.PeriodRead.class::isInstance).map(ExtractedValue.PeriodRead.class::cast),
                byPath.keySet().stream().anyMatch(path -> path.startsWith(annexure + ".")),
                value.apply("REGIME_OPT_OUT").filter(ExtractedValue.FlagRead.class::isInstance).map(read -> ((ExtractedValue.FlagRead) read).value()),
                amount(value.apply("SALARY_FROM_OTHER_EMPLOYERS")), amount(value.apply("STANDARD_DEDUCTION")));
    }

    private static Optional<Money> amount(Optional<ExtractedValue> value) {
        return value.filter(ExtractedValue.MoneyRead.class::isInstance).map(read -> ((ExtractedValue.MoneyRead) read).amount());
    }

    /** Employers' periods in order: a day nobody covers is a gap, a day two cover an overlap (C2a: both lawful, both worth a look). */
    private static List<AssemblyNotice> periods(List<Certificate> year) {
        List<Certificate> dated = year.stream().filter(certificate -> certificate.period().isPresent())
                .sorted(Comparator.comparing(certificate -> certificate.period().orElseThrow().from())).toList();
        List<AssemblyNotice> notices = new ArrayList<>();
        for (int at = 1; at < dated.size(); at++) {
            Certificate before = dated.get(at - 1);
            Certificate after = dated.get(at);
            LocalDate ends = before.period().orElseThrow().to();
            LocalDate starts = after.period().orElseThrow().from();
            List<String> both = List.of(before.documentId(), after.documentId());
            if (starts.isAfter(ends.plusDays(1))) {
                notices.add(new AssemblyNotice(AssemblyNotice.Level.ATTENTION, "No certificate covers " + ends.plusDays(1).format(DAY) + " to "
                        + starts.minusDays(1).format(DAY) + ": unemployed, or a certificate not uploaded", both, List.of()));
            } else if (!starts.isAfter(ends)) {
                LocalDate overlapEnds = after.period().orElseThrow().to().isBefore(ends) ? after.period().orElseThrow().to() : ends;
                notices.add(new AssemblyNotice(AssemblyNotice.Level.ATTENTION, before.tan().orElse(before.documentId()) + " and "
                        + after.tan().orElse(after.documentId()) + " both cover " + starts.format(DAY) + " to " + overlapEnds.format(DAY)
                        + ": two employments at once, or a revised certificate", both, List.of()));
            }
        }
        return notices;
    }

    /** {@code DOC-F130-ANNEX-001}: the last employer's Part C should carry the earlier salary in row 1(e). */
    private static Optional<AssemblyNotice> previousSalary(List<Certificate> year) {
        Certificate last = year.stream().filter(Certificate::hasPartC).findFirst().orElseThrow();
        if (last.otherSalary().isPresent() && last.otherSalary().get().isGreaterThan(Money.ZERO)) {
            return Optional.empty();
        }
        return Optional.of(new AssemblyNotice(AssemblyNotice.Level.ACTION_REQUIRED, "Only one employer issued Part C, and its salary from"
                + " other employers is nil: a previous employer's salary appears not to have been declared", List.of(last.documentId()),
                List.of("TDS-SAL-12B-001")));
    }

    /**
     * One year's standard deduction, not one per employer (C4a; SAL-STDDED-001/002): the ceiling is the regime row A states,
     * the largest where the certificates differ.
     */
    private static Optional<AssemblyNotice> standardDeduction(List<Certificate> year, SalaryCertificateLaw law) {
        Money total = year.stream().map(Certificate::standardDeduction).flatMap(Optional::stream).reduce(Money.ZERO, Money::plus);
        Optional<RegimeCode> regime = year.stream().map(Certificate::optsOut).flatMap(Optional::stream)
                .flatMap(optsOut -> law.standardDeductionCeilings().keySet().stream()
                        .filter(code -> code.equals(law.defaultRegime()) != optsOut))
                .max(Comparator.comparing(code -> law.standardDeductionCeilings().get(code)));
        if (regime.isEmpty() || !total.isGreaterThan(law.standardDeductionCeilings().get(regime.get()))) {
            return Optional.empty();
        }
        Money ceiling = law.standardDeductionCeilings().get(regime.get());
        return Optional.of(new AssemblyNotice(AssemblyNotice.Level.ACTION_REQUIRED, "Each employer gave a standard deduction: "
                + total.inIndianNotation() + " in all, where one year allows " + ceiling.inIndianNotation() + " under " + regime.get().value()
                + "; " + total.minus(ceiling).inIndianNotation() + " more is taxable at filing",
                year.stream().filter(certificate -> certificate.standardDeduction().isPresent()).map(Certificate::documentId).toList(),
                List.of("SAL-STDDED-001", "SAL-STDDED-002")));
    }
}
