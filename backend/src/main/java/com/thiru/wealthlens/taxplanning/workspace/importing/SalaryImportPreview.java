package com.thiru.wealthlens.taxplanning.workspace.importing;

import com.thiru.wealthlens.taxengine.api.PayLineComponent;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * What a salary workbook would put on a draft, before anything is written (F28.4's review; W-133a C1a): each row matched to a
 * component, each row returned for the user to map, and the components of the draft's year to map them to. Nothing here is
 * stored; W-133b applies it.
 *
 * @param matched each row whose label is a component's code or name, exactly
 * @param returned each row that is not, with its amount, never guessed and never dropped (F28.2, F28.3)
 * @param matchedTotal the matched rows' annual amounts added
 * @param components what a returned row may be mapped to: the year's pay-line components
 */
public record SalaryImportPreview(String draftId, String taxYear, List<ImportedLine> matched, List<ReturnedRow> returned,
        Money matchedTotal, List<PayLineComponent> components) {

    public SalaryImportPreview {
        matched = List.copyOf(matched);
        returned = List.copyOf(returned);
        components = List.copyOf(components);
    }

    /**
     * A row matched to a component.
     *
     * @param annualAmount the row's annual amount, or its monthly amount × 12 where only that is given
     * @param fromMonthly whether {@code annualAmount} is the monthly amount × 12
     */
    public record ImportedLine(int row, String label, String code, String displayName, Money annualAmount, boolean fromMonthly) {
    }

    /**
     * A row for the user to map, or to fill in.
     *
     * @param annualAmount as on {@link ImportedLine}; empty where the row gives none
     * @param why why it came back, in words
     */
    public record ReturnedRow(int row, String label, java.util.Optional<Money> annualAmount, boolean fromMonthly, String why) {
    }
}
