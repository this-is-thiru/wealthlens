package com.thiru.wealthlens.taxplanning.workspace.importing;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.PayLineComponent;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.isolation.ChildReply;
import com.thiru.wealthlens.taxplanning.isolation.IsolatedChild;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Previews a salary workbook against a draft (F28; W-133a): the workbook is read in a child process ({@link SalaryStructureWorkbook},
 * C3a), and each row's label is matched to a pay-line component of the draft's year by its code or its name, exactly, once spaces
 * and case are set aside (C4a). A row that does not match comes back with its amount for the user to map: never guessed, never
 * dropped (F28.2, F28.3). Nothing is stored. No figure reaches the log (O1.1).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SalaryImportService {

    /** A salary workbook is a few kilobytes; a megabyte leaves room for formatting (W-133a's number, the owner's to change). */
    static final int MAX_BYTES = 1_048_576;
    private static final int MONTHS = 12;
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .build();

    private final DraftService drafts;
    private final TaxCalculator taxCalculator;
    private final DocumentSettings settings;
    /** The workbook's child process ({@link SalaryImportConfig}). */
    private final IsolatedChild reader;

    /** @throws BadRequestException for another account's draft, an empty, oversized or unreadable workbook, or an amount that is no amount */
    public SalaryImportPreview preview(String email, String draftId, byte[] workbook) {
        TaxYearId year = TaxYearId.of(drafts.ownedDraft(email, draftId).draft().getTaxYearStart());
        if (workbook.length == 0) {
            throw new BadRequestException("The workbook is empty");
        }
        if (workbook.length > MAX_BYTES) {
            throw new BadRequestException("A salary workbook is at most " + MAX_BYTES + " bytes");
        }
        List<WorkbookRows.Row> rows = rowsOf(workbook);
        List<PayLineComponent> components = taxCalculator.payLineComponents(year);
        Map<String, PayLineComponent> byName = new HashMap<>();
        components.forEach(component -> {
            byName.put(key(component.code()), component);
            byName.put(key(component.displayName()), component);
        });
        List<SalaryImportPreview.ImportedLine> matched = new ArrayList<>();
        List<SalaryImportPreview.ReturnedRow> returned = new ArrayList<>();
        for (WorkbookRows.Row row : rows) {
            Optional<Money> amount = amount(row);
            boolean fromMonthly = row.annual().isEmpty() && !row.monthly().isEmpty();
            PayLineComponent component = byName.get(key(row.label()));
            if (row.label().isEmpty()) {
                returned.add(new SalaryImportPreview.ReturnedRow(row.number(), row.label(), amount, fromMonthly, "The row names no component"));
            } else if (component == null) {
                returned.add(new SalaryImportPreview.ReturnedRow(row.number(), row.label(), amount, fromMonthly,
                        "No pay-line component of " + year.label() + " is named this: choose one"));
            } else if (amount.isEmpty()) {
                returned.add(new SalaryImportPreview.ReturnedRow(row.number(), row.label(), amount, fromMonthly, "The row gives no amount"));
            } else {
                matched.add(new SalaryImportPreview.ImportedLine(row.number(), row.label(), component.code(), component.displayName(), amount.get(),
                        fromMonthly));
            }
        }
        log.info("Previewed a salary workbook for draft {}: {} row(s) matched, {} returned", draftId, matched.size(), returned.size());
        return new SalaryImportPreview(draftId, year.label(), matched, returned,
                matched.stream().map(SalaryImportPreview.ImportedLine::annualAmount).reduce(Money.ZERO, Money::plus), components);
    }

    private List<WorkbookRows.Row> rowsOf(byte[] workbook) {
        WorkbookRows read = switch (reader.run("SALARY_STRUCTURE", workbook, settings.isolation().uploadReadBudget())) {
            case ChildReply.Answered answered -> understood(answered.bytes());
            case ChildReply.Failed failed -> throw new BadRequestException("The workbook could not be read: " + failed.reason());
        };
        if (read.outcome() == WorkbookRows.Outcome.UNREADABLE) {
            throw new BadRequestException(read.reason());
        }
        return read.rows();
    }

    private static WorkbookRows understood(byte[] reply) {
        try {
            return STRICT.readValue(reply, WorkbookRows.class);
        } catch (JacksonException e) {
            throw new BadRequestException("The workbook could not be read: the reader's reply was not understood");
        }
    }

    /** The annual amount, else the monthly × 12 (C5a); a cell that is no amount, or a negative one, is refused by its row. */
    private static Optional<Money> amount(WorkbookRows.Row row) {
        if (!row.annual().isEmpty()) {
            return Optional.of(rupees(row.annual(), row.number(), "annual"));
        }
        if (!row.monthly().isEmpty()) {
            return Optional.of(rupees(row.monthly(), row.number(), "monthly").times(MONTHS));
        }
        return Optional.empty();
    }

    /** Digits, with Indian or Western grouping commas, and at most two decimals: the forms a CTC sheet writes a rupee figure in. */
    private static Money rupees(String text, int row, String which) {
        String digits = text.replace(",", "");
        if (!digits.matches("\\d+(\\.\\d{1,2})?")) {
            throw new BadRequestException("Row " + row + ": the " + which + " amount is not a rupee figure");
        }
        return Money.of(digits);
    }

    private static String key(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
