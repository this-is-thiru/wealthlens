package com.thiru.wealthlens.taxplanning.workspace.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;
import com.thiru.wealthlens.taxplanning.isolation.IsolatedChild;
import com.thiru.wealthlens.taxplanning.workspace.DraftSaveRetry;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.DraftView;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.WorkbookImport;
import com.thiru.wealthlens.testsupport.LogCapture;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Period;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** A salary workbook previewed against a TY 2026-27 draft, read in a real child process (W-133a C3a – C5a). */
class SalaryImportServiceTest {

    private static final DocumentSettings.Isolation ISOLATION = new DocumentSettings.Isolation(256, 30, Duration.ofSeconds(20), 16_777_216);
    private static final DocumentSettings SETTINGS = new DocumentSettings(20_971_520, 50, Period.ofDays(1), Duration.ofSeconds(30), true,
            new DocumentSettings.Scan(true, "localhost", 3310, Duration.ofSeconds(60)), ISOLATION);
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks", Set.of(TaxYearId.of(2026))));

    private final DraftSaveRetry saves = mock(DraftSaveRetry.class);

    private SalaryImportService service() {
        DraftService drafts = mock(DraftService.class);
        FilingDraft draft = new FilingDraft();
        draft.setTaxYearStart(2026);
        when(drafts.ownedDraft("owner@example.com", "draft-1")).thenReturn(new DraftService.OwnedDraft(draft, null));
        return new SalaryImportService(drafts, saves, CALCULATOR, SETTINGS, IsolatedChild.of(SalaryStructureWorkbook.class, ISOLATION));
    }

    @Test
    void preview_whenLabelsAreNamesOrCodes_matchesThemAndReturnsTheRestWithTheirAmounts() {
        // Given — "Basic salary" is BASIC's name, "hra" HRA's code; "Conveyance" and "Basic pay" are neither, a near miss included;
        // HRA's monthly 25,000 × 12 = 3,00,000
        byte[] workbook = Workbooks.sheet(new Object[] {"Component", "Annual Amount", "Monthly Amount"},
                new Object[] {"Basic salary", 600000, null}, new Object[] {"hra", null, 25000}, new Object[] {"Conveyance", 19200, null},
                new Object[] {"Basic pay", 100000, null});

        // When
        SalaryImportPreview preview = service().preview("owner@example.com", "draft-1", workbook);

        // Then — 6,00,000 + 3,00,000 = 9,00,000 matched; nothing guessed, nothing dropped
        assertThat(preview.taxYear()).isEqualTo("2026-27");
        assertThat(preview.matched()).containsExactly(
                new SalaryImportPreview.ImportedLine(2, "Basic salary", "BASIC", "Basic salary", Money.of("600000"), false),
                new SalaryImportPreview.ImportedLine(3, "hra", "HRA", "House rent allowance", Money.of("300000"), true));
        assertThat(preview.matchedTotal()).isEqualTo(Money.of("900000"));
        assertThat(preview.returned()).extracting(SalaryImportPreview.ReturnedRow::row, SalaryImportPreview.ReturnedRow::label)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(4, "Conveyance"), org.assertj.core.groups.Tuple.tuple(5, "Basic pay"));
        assertThat(preview.returned().getFirst().annualAmount()).isEqualTo(Optional.of(Money.of("19200")));
        assertThat(preview.returned().getFirst().why()).isEqualTo("No pay-line component of 2026-27 is named this: choose one");
        assertThat(preview.components()).anySatisfy(component -> assertThat(component.code()).isEqualTo("CONVEYANCE_ALLOWANCE"));
    }

    @Test
    void preview_whenAnAmountIsNoRupeeFigure_refusesNamingTheRow() {
        // Given — a negative amount on row 3
        byte[] workbook = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Bonus", 1000},
                new Object[] {"Commission", -500});

        // When / Then
        assertThatThrownBy(() -> service().preview("owner@example.com", "draft-1", workbook)).isInstanceOf(BadRequestException.class)
                .hasMessage("Row 3: the annual amount is not a rupee figure");
    }

    @Test
    void preview_whenAMatchedRowGivesNoAmount_returnsItRatherThanDroppingIt() {
        // When
        SalaryImportPreview preview = service().preview("owner@example.com", "draft-1",
                Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Bonus", null}, new Object[] {null, 500}));

        // Then
        assertThat(preview.returned()).extracting(SalaryImportPreview.ReturnedRow::why).containsExactly("The row gives no amount",
                "The row names no component");
    }

    @Test
    void preview_whenTheBytesAreNoWorkbook_refusesAsUnreadable() {
        // When / Then — the child exits with an error
        assertThatThrownBy(() -> service().preview("owner@example.com", "draft-1", "label,amount".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(BadRequestException.class).hasMessageStartingWith("The workbook could not be read: The reader stopped (exit ");
    }

    @Test
    void preview_whenEmptyOrOverOneMebibyte_refusesBeforeReading() {
        // When / Then
        assertThatThrownBy(() -> service().preview("owner@example.com", "draft-1", new byte[0])).hasMessage("The workbook is empty");
        assertThatThrownBy(() -> service().preview("owner@example.com", "draft-1", new byte[1_048_577]))
                .hasMessage("A salary workbook is at most 1048576 bytes");
    }

    @Test
    void preview_whenAWorkbookIsRead_logsNoFigure() {
        // Given
        byte[] workbook = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Basic salary", 987654});

        // When
        try (LogCapture logs = LogCapture.on(SalaryImportService.class)) {
            service().preview("owner@example.com", "draft-1", workbook);

            // Then — counts, never amounts or labels (O1.1)
            assertThat(logs.messages()).isNotEmpty().noneMatch(message -> message.contains("987654") || message.contains("Basic"));
        }
    }

    /** Rows 2 "Basic salary" 6,00,000 (matched), 3 "Conveyance" 19,200 and 4 "Basic pay" 1,00,000 (both returned). */
    private static final byte[] CTC = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Basic salary", 600000},
            new Object[] {"Conveyance", 19200}, new Object[] {"Basic pay", 100000});
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ObjectNode employment() {
        return (ObjectNode) JSON.readTree("""
                {"termsIncludeDearnessAllowance": false, "employerKind": "OTHER", "dearnessAllowanceCountsForRetirement": false,
                 "companyEmployer": null}""");
    }

    private static SalaryImportDecisions decisions(List<SalaryImportDecisions.Mapping> mappings, List<Integer> leftOut) {
        return new SalaryImportDecisions(mappings, leftOut, employment());
    }

    @Test
    void apply_whenEveryReturnedRowIsDecided_savesOneEmploymentAndRecordsTheWorkbook() throws Exception {
        // Given — row 3 mapped to the conveyance allowance, row 4 left out
        SalaryImportDecisions decided = decisions(List.of(new SalaryImportDecisions.Mapping(3, "CONVEYANCE_ALLOWANCE")), List.of(4));
        when(saves.importInto(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(new DraftView("draft-1", "workspace-1", "Mine", DraftStatus.DRAFT, 8, null, null, null));

        // When
        service().apply("owner@example.com", "draft-1", 7, CTC, decided);

        // Then — one add of an employment with BASIC 6,00,000 and CONVEYANCE_ALLOWANCE 19,200, at revision 7
        ArgumentCaptor<JsonNode> patch = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<WorkbookImport> imported = ArgumentCaptor.forClass(WorkbookImport.class);
        verify(saves).importInto(eq("owner@example.com"), eq("draft-1"), eq(7), patch.capture(), imported.capture());
        assertThat(patch.getValue()).hasSize(1);
        assertThat(patch.getValue().get(0).get("path").asString()).isEqualTo("/entered/employments/-");
        JsonNode value = patch.getValue().get(0).get("value");
        assertThat(value.get("employerKind").asString()).isEqualTo("OTHER");
        assertThat(value.get("components")).hasSize(2);
        assertThat(value.get("components").get(0).get("code").get("value").asString()).isEqualTo("BASIC");
        assertThat(value.get("components").get(0).get("annualAmount").get("amountInRupees").decimalValue()).isEqualByComparingTo("600000");
        assertThat(value.get("components").get(1).get("code").get("value").asString()).isEqualTo("CONVEYANCE_ALLOWANCE");
        assertThat(value.get("components").get(1).get("annualAmount").get("amountInRupees").decimalValue()).isEqualByComparingTo("19200");
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CTC));
        assertThat(imported.getValue()).isEqualTo(new WorkbookImport(sha256, List.of(2, 3), List.of(3), List.of(4)));
    }

    @Test
    void apply_whenAReturnedRowIsUndecided_refusesNamingIt() {
        // When / Then — row 4 is neither mapped nor left out
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, CTC,
                decisions(List.of(new SalaryImportDecisions.Mapping(3, "CONVEYANCE_ALLOWANCE")), List.of())))
                .isInstanceOf(BadRequestException.class).hasMessage("Rows [4] need a decision: map each to a component, or leave it out");
    }

    @Test
    void apply_whenARowIsDecidedTwiceOrWasNotReturned_refusesNamingIt() {
        // When / Then
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, CTC,
                decisions(List.of(new SalaryImportDecisions.Mapping(3, "CONVEYANCE_ALLOWANCE")), List.of(3, 4))))
                .hasMessage("Rows decided more than once: [3]");
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, CTC,
                decisions(List.of(new SalaryImportDecisions.Mapping(3, "CONVEYANCE_ALLOWANCE")), List.of(2, 4))))
                .hasMessage("Rows not returned for a decision: [2]");
    }

    @Test
    void apply_whenAMappingNamesNoComponentOfTheYear_refusesNamingTheRow() {
        // When / Then
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, CTC,
                decisions(List.of(new SalaryImportDecisions.Mapping(3, "TUITION_FEES")), List.of(4))))
                .hasMessage("Row 3: TUITION_FEES is no pay-line component of 2026-27");
    }

    @Test
    void apply_whenTwoRowsBecomeOneComponent_refusesNamingBothRows() {
        // Given — "Bonus" matches BONUS; row 3 is mapped to BONUS too
        byte[] workbook = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Bonus", 1000},
                new Object[] {"Festival bonus", 2000});

        // When / Then
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, workbook,
                decisions(List.of(new SalaryImportDecisions.Mapping(3, "BONUS")), List.of())))
                .hasMessage("Rows 2 and 3 are both BONUS: merge them in the sheet");
    }

    @Test
    void apply_whenEveryRowIsLeftOutOrTheEmploymentCarriesComponents_refuses() {
        // Given
        byte[] onlyReturned = Workbooks.sheet(new Object[] {"Component", "Annual Amount"}, new Object[] {"Basic pay", 1000});
        ObjectNode withComponents = employment();
        withComponents.putArray("components");

        // When / Then
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, onlyReturned, decisions(List.of(), List.of(2))))
                .hasMessage("Nothing to import: every row was left out");
        assertThatThrownBy(() -> service().apply("owner@example.com", "draft-1", 7, CTC,
                new SalaryImportDecisions(List.of(new SalaryImportDecisions.Mapping(3, "CONVEYANCE_ALLOWANCE")), List.of(4), withComponents)))
                .hasMessage("The employment's pay lines are the workbook's: send it without components");
    }
}
