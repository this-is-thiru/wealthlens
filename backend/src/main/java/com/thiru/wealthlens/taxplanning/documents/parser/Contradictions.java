package com.thiru.wealthlens.taxplanning.documents.parser;

import com.thiru.wealthlens.taxengine.api.CertificateLayout;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedAgreement;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedSum;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.PrintedTable;
import com.thiru.wealthlens.taxengine.api.CertificateLayout.SumCell;
import com.thiru.wealthlens.taxengine.api.SalaryCertificateLaw;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A certificate checked against its own arithmetic ({@code DR 06} G5.2; W-128a, D-750): a sum the form prints whose cells are
 * all read must hold exactly, and a table's Total amount must be the sum of its rows. A contradiction is never resolved by
 * picking a side: it is named with both figures, and every cell of it drops to {@link #CONTRADICTED_CONFIDENCE}.
 */
final class Contradictions {

    /** Below L3's 0.70 floor, so a contradicted cell is never trusted above an inferred one (D-750 C2a, adopted by the owner). */
    static final double CONTRADICTED_CONFIDENCE = 0.50;

    private Contradictions() {
    }

    /** The fields, contradicted cells lowered; a note for each contradiction, and one for each difference shown. */
    record Checked(List<ExtractedField> fields, List<String> contradictions, List<String> differences) {
    }

    static Checked check(List<ExtractedField> fields, CertificateLayout layout, SalaryCertificateLaw law, TaxYearId year) {
        Map<String, ExtractedField> byPath = new LinkedHashMap<>();
        fields.forEach(field -> byPath.put(field.fieldPath(), field));
        List<String> notes = new ArrayList<>();
        layout.checks().sums().forEach(sum -> checkSum(sum, byPath, notes));
        layout.tables().forEach(table -> table.totalCells().forEach(cell -> checkTotal(table, cell.get(0), byPath, notes)));
        List.copyOf(byPath.values()).forEach(field -> checkPeriod(field, year, byPath, notes));
        List<String> differences = new ArrayList<>();
        layout.checks().agreements().forEach(agreement -> checkAgreement(agreement, byPath, differences));
        factsByPart(layout).values().forEach(facts -> {
            checkStandardDeduction(facts, law, byPath, notes);
            checkCess(facts, law, byPath, notes);
        });
        return new Checked(List.copyOf(byPath.values()), notes, differences);
    }

    /** A period inside the tax year being read, its first day not after its last (W-128b C2a). */
    private static void checkPeriod(ExtractedField field, TaxYearId year, Map<String, ExtractedField> byPath, List<String> notes) {
        if (field.interpretedValue().orElse(null) instanceof ExtractedValue.PeriodRead period) {
            String printed = period.from().format(DAY) + " to " + period.to().format(DAY);
            if (period.from().isAfter(period.to())) {
                notes.add("Contradiction: " + field.fieldPath() + " printed " + printed + ", its first day after its last");
                lower(byPath, field.fieldPath());
            } else if (!year.contains(period.from()) || !year.contains(period.to())) {
                notes.add("Contradiction: " + field.fieldPath() + " printed " + printed + ", outside tax year " + year.label());
                lower(byPath, field.fieldPath());
            }
        }
    }

    /**
     * Two amounts that should be near each other (W-128b): beyond the tolerance the difference is shown and both are kept as
     * read, since an employer may deduct more or less than the year's tax (C4a).
     */
    private static void checkAgreement(PrintedAgreement agreement, Map<String, ExtractedField> byPath, List<String> differences) {
        Optional<Money> printed = amount(byPath.get(agreement.printed()));
        Optional<Money> near = amount(byPath.get(agreement.near()));
        if (printed.isEmpty() || near.isEmpty()) {
            return;
        }
        Money apart = Money.max(printed.get(), near.get()).minus(Money.min(printed.get(), near.get()));
        if (apart.isGreaterThan(agreement.tolerance())) {
            differences.add("Differs: " + agreement.printed() + " " + printed.get().inIndianNotation() + " and " + agreement.near() + " "
                    + near.get().inIndianNotation() + " are " + apart.inIndianNotation() + " apart (" + agreement.basis() + ")");
        }
    }

    /** Exactly (D-750 C4a), and only when every cell is read: an inferred cell agrees by construction. */
    private static void checkSum(PrintedSum sum, Map<String, ExtractedField> byPath, List<String> notes) {
        List<SumCell> cells = Stream.concat(Stream.of(sum.target()), sum.terms().stream()).toList();
        if (!cells.stream().allMatch(cell -> amount(byPath.get(cell.path())).isPresent())) {
            return;
        }
        Money printed = amount(byPath.get(sum.target().path())).orElseThrow();
        Money gives = sum.terms().stream().map(term -> amount(byPath.get(term.path())).orElseThrow().times(term.sign()))
                .reduce(Money.ZERO, Money::plus);
        if (printed.compareTo(gives) != 0) {
            notes.add("Contradiction: " + sum.target().path() + " printed " + printed.inIndianNotation()
                    + ", the sum printed for it gives " + gives.inIndianNotation());
            cells.forEach(cell -> lower(byPath, cell.path()));
        }
    }

    /**
     * A Total cell whose first column is an amount (C2a), against that column's rows: a blank cell is nothing, a cell
     * printing text L2 could not read leaves the column unchecked (C1a).
     */
    private static void checkTotal(PrintedTable table, String key, Map<String, ExtractedField> byPath, List<String> notes) {
        boolean isAmount = table.columns().stream().anyMatch(column -> column.key().equals(key) && "AMOUNT".equals(column.kind()));
        String totalPath = table.path() + ".total." + key;
        Optional<Money> total = amount(byPath.get(totalPath));
        Pattern row = Pattern.compile(Pattern.quote(table.path()) + "\\.row\\d+\\." + Pattern.quote(key));
        List<String> rows = byPath.keySet().stream().filter(path -> row.matcher(path).matches()).toList();
        if (!isAmount || total.isEmpty() || rows.stream().anyMatch(path -> amount(byPath.get(path)).isEmpty())) {
            return;
        }
        Money added = rows.stream().map(path -> amount(byPath.get(path)).orElseThrow()).reduce(Money.ZERO, Money::plus);
        if (total.get().compareTo(added) != 0) {
            notes.add("Contradiction: " + totalPath + " printed " + total.get().inIndianNotation() + ", its rows add to "
                    + added.inIndianNotation());
            lower(byPath, totalPath);
            rows.forEach(path -> lower(byPath, path));
        }
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /** Each part's lines that state a fact, by fact (W-128c): a part names each fact once ({@code CertificateSpec}). */
    private static Map<String, Map<String, String>> factsByPart(CertificateLayout layout) {
        Map<String, Map<String, String>> byPart = new LinkedHashMap<>();
        layout.lines().stream().filter(line -> line.fact().isPresent()).forEach(line -> byPart
                .computeIfAbsent(line.path().substring(0, line.path().lastIndexOf('.')), part -> new LinkedHashMap<>())
                .put(line.fact().orElseThrow(), line.path()));
        return byPart;
    }

    /**
     * The standard deduction against row A's regime (G5.2; W-128c C3a): above that regime's ceiling, or another regime's
     * ceiling where it is not this one's, is a contradiction; below the ceiling may be a salary under it ("whichever is less").
     */
    private static void checkStandardDeduction(Map<String, String> facts, SalaryCertificateLaw law, Map<String, ExtractedField> byPath,
            List<String> notes) {
        String optOutPath = facts.get("REGIME_OPT_OUT");
        String deductionPath = facts.get("STANDARD_DEDUCTION");
        Optional<Boolean> optsOut = flag(byPath.get(optOutPath));
        Optional<Money> deduction = amount(byPath.get(deductionPath));
        if (optsOut.isEmpty() || deduction.isEmpty()) {
            return;
        }
        Map<RegimeCode, Money> ceilings = law.standardDeductionCeilings();
        List<RegimeCode> regimes = ceilings.keySet().stream().filter(regime -> regime.equals(law.defaultRegime()) != optsOut.get())
                .sorted(java.util.Comparator.comparing(RegimeCode::value)).toList();
        Optional<Money> ceiling = regimes.stream().map(ceilings::get).max(Money::compareTo);
        if (ceiling.isEmpty()) {
            return;
        }
        String row = optOutPath + "'s " + (optsOut.get() ? "Yes leaves" : "No keeps");
        String named = regimes.stream().map(RegimeCode::value).collect(java.util.stream.Collectors.joining(", "));
        Optional<RegimeCode> other = ceilings.entrySet().stream().filter(entry -> !regimes.contains(entry.getKey())
                && entry.getValue().compareTo(deduction.get()) == 0).map(Map.Entry::getKey).findFirst();
        if (deduction.get().isGreaterThan(ceiling.get())) {
            notes.add("Contradiction: " + deductionPath + " printed " + deduction.get().inIndianNotation() + ", above the "
                    + ceiling.get().inIndianNotation() + " standard deduction of " + named + " that " + row);
        } else if (other.isPresent() && deduction.get().compareTo(ceiling.get()) != 0) {
            notes.add("Contradiction: " + deductionPath + " printed " + deduction.get().inIndianNotation() + ", the standard deduction of "
                    + other.get().value() + ", but " + row + " " + named + " (" + ceiling.get().inIndianNotation() + ")");
        } else {
            return;
        }
        lower(byPath, optOutPath);
        lower(byPath, deductionPath);
    }

    /** The cess against its rate × (tax + surcharge − rebate), within ₹10 (G5.2; W-128c C4a); an unprinted term is nothing. */
    private static void checkCess(Map<String, String> facts, SalaryCertificateLaw law, Map<String, ExtractedField> byPath,
            List<String> notes) {
        List<String> terms = Stream.of("TAX_ON_TOTAL_INCOME", "SURCHARGE", "REBATE").map(facts::get).toList();
        String cessPath = facts.get("CESS");
        Optional<Money> cess = amount(byPath.get(cessPath));
        Optional<Money> tax = amount(byPath.get(terms.get(0)));
        if (terms.contains(null) || cess.isEmpty() || tax.isEmpty()
                || terms.stream().anyMatch(path -> byPath.containsKey(path) && amount(byPath.get(path)).isEmpty())) {
            return;
        }
        Money base = tax.get().plus(amount(byPath.get(terms.get(1))).orElse(Money.ZERO)).minus(amount(byPath.get(terms.get(2))).orElse(Money.ZERO));
        Money gives = law.cessRate().of(base);
        if (Money.max(cess.get(), gives).minus(Money.min(cess.get(), gives)).isGreaterThan(Money.TEN)) {
            notes.add("Contradiction: " + cessPath + " printed " + cess.get().inIndianNotation() + ", " + law.cessRate().asPercentLabel()
                    + " of " + terms.get(0) + " + " + terms.get(1) + " − " + terms.get(2) + " gives " + gives.inIndianNotation());
            lower(byPath, cessPath);
            terms.stream().filter(byPath::containsKey).forEach(path -> lower(byPath, path));
        }
    }

    private static Optional<Boolean> flag(ExtractedField field) {
        return Optional.ofNullable(field).flatMap(ExtractedField::interpretedValue).filter(ExtractedValue.FlagRead.class::isInstance)
                .map(value -> ((ExtractedValue.FlagRead) value).value());
    }

    private static void lower(Map<String, ExtractedField> byPath, String path) {
        ExtractedField field = byPath.get(path);
        byPath.put(path, new ExtractedField(field.fieldPath(), field.rawText(), field.interpretedValue(), CONTRADICTED_CONFIDENCE,
                field.location(), field.mappedTo()));
    }

    private static Optional<Money> amount(ExtractedField field) {
        return Optional.ofNullable(field).flatMap(ExtractedField::interpretedValue)
                .filter(ExtractedValue.MoneyRead.class::isInstance).map(value -> ((ExtractedValue.MoneyRead) value).amount());
    }
}
