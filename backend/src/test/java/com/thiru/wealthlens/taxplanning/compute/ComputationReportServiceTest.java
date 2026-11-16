package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One stored computation as a PDF (W-123a). The expected text is the stored computation's own — what the person was
 * shown — and the year's lock file, never the service's output: the report is a projection, so the test is that nothing
 * is dropped, moved or reworded.
 */
class ComputationReportServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final ComputationReportService SERVICE = new ComputationReportService(null, null, CALCULATOR, engine());

    private static TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        // Spring's engine, as the application runs it: expressions are SpEL, not OGNL
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** Basic 12,00,000 in the old regime, TY 2026-27: a salary, a standard deduction, slabs, cess and rounding. */
    private static ComputedTax salaried() {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment pay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1200000"))),
                false, EmployerKind.OTHER, false, Optional.empty());
        return CALCULATOR.compute(new EngineInputs(TaxYearId.of(2026), new RegimeCode("FIRST_SCHEDULE_RATES"),
                plain.temporal(), EnteredFacts.none().withEmployments(List.of(pay)), plain.assessee(), plain.family(),
                plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing()));
    }

    /** What the rules read then are pinned at: today's, as a computation made today stores them (W-138). */
    private static Map<String, String> pinnedNow(ComputedTax tax, TaxYearId year) {
        return CALCULATOR.ruleChecksums(year, tax.trace().stream().map(ComputedTax.Step::rule).collect(Collectors.toSet()));
    }

    private static String text(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder text = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page));
        }
        return squeezed(text.toString());
    }

    /** Line breaks fall where a cell wraps, so text is compared with every space removed. */
    private static String squeezed(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static JsonNode lock(String year) throws IOException {
        try (InputStream in = ComputationReportServiceTest.class.getResourceAsStream("/rulepacks/" + year + ".lock.json")) {
            return JsonMapper.builder().build().readTree(in);
        }
    }

    /** A stored view of {@code tax} as one stored before W-122b reads: it says neither coarse nor final. */
    private static DraftComputationView view(ComputedTax tax) {
        return new DraftComputationView(tax, "draft", 1, false, Optional.empty(), Optional.empty(), List.of());
    }

    private static DraftComputationView coarse(ComputedTax tax, boolean coarse) {
        Optional<Band> band = coarse ? Optional.of(new Band(Money.of("163800"), Money.of("88920"), "coarse"))
                : Optional.empty();
        return new DraftComputationView(tax, "draft", 1, false, Optional.of(coarse), band, List.of());
    }

    @Test
    void model_whenTheStoredViewWasCoarse_notesItWithItsBand() throws IOException {
        // Given — W-122b's persona's band, 88,920 to 1,63,800
        ComputedTax tax = salaried();

        // When
        ComputationReport report = SERVICE.model(coarse(tax, true), pinnedNow(tax, TaxYearId.of(2026)));
        String pdf = text(SERVICE.render(report));

        // Then
        assertThat(report.coarseNote()).hasValueSatisfying(note -> assertThat(note)
                .startsWith("This figure was coarse when it was computed")
                .contains("between ₹88,920 and ₹1,63,800"));
        assertThat(pdf).contains(squeezed("between ₹88,920 and ₹1,63,800"));
    }

    @Test
    void model_whenTheStoredViewWasNotCoarseOrSaidNothing_hasNoCoarseNote() {
        // Given
        ComputedTax tax = salaried();

        // When / Then
        assertThat(SERVICE.model(coarse(tax, false), pinnedNow(tax, TaxYearId.of(2026))).coarseNote()).isEmpty();
        assertThat(SERVICE.model(view(tax), pinnedNow(tax, TaxYearId.of(2026))).coarseNote()).isEmpty();
    }

    @Test
    void render_whenAComputationIsReported_printsEveryLineItsRuleAndFigureAndTheDisclaimerOnce() throws IOException {
        // Given
        ComputedTax tax = salaried();

        // When
        String pdf = text(SERVICE.render(SERVICE.model(view(tax), pinnedNow(tax, TaxYearId.of(2026)))));

        // Then
        assertThat(tax.trace()).hasSizeGreaterThan(5);
        assertThat(tax.trace()).allSatisfy(step -> {
            assertThat(pdf).contains(squeezed(step.explanation()));
            assertThat(pdf).contains(step.rule());
            step.out().ifPresent(out -> assertThat(pdf).contains(squeezed(out.inIndianNotation())));
        });
        assertThat(pdf).contains(tax.receipt(), squeezed(tax.finalAmount().inIndianNotation()));
        assertThat(pdf.split("nottaxadvice", -1)).hasSize(2);
        assertThat(pdf).contains(squeezed("Income-tax Act, 2025 (30 of 2025)"), squeezed("Finance Act, 2026 (4 of 2026)"));
    }

    @Test
    void model_whenAComputationIsReported_groupsEachLineUnderItsKindInSequenceOrder() {
        // Given
        ComputedTax tax = salaried();

        // When
        ComputationReport report = SERVICE.model(view(tax), pinnedNow(tax, TaxYearId.of(2026)));

        // Then — every line once; within a section, ascending; a section holds one kind
        assertThat(report.sections().stream().flatMap(section -> section.lines().stream())
                .map(ComputationReport.Line::sequence))
                .containsExactlyInAnyOrderElementsOf(tax.trace().stream().map(ComputedTax.Step::sequence).toList());
        Map<Integer, String> kindOf = tax.trace().stream()
                .collect(Collectors.toMap(ComputedTax.Step::sequence, ComputedTax.Step::kind));
        assertThat(report.sections()).allSatisfy(section -> {
            assertThat(section.lines()).extracting(ComputationReport.Line::sequence).isSorted();
            assertThat(section.lines().stream().map(line -> kindOf.get(line.sequence())).distinct()).hasSize(1);
        });
        assertThat(report.packNote()).isEmpty();
        assertThat(report.sections()).allSatisfy(section -> assertThat(section.lines())
                .allSatisfy(line -> assertThat(line.mark()).isEmpty()));
    }

    /**
     * A TY 2025-26 computation whose trace reads {@code INT-CALCULATION}, unverified in that year's version
     * ({@code law/rules/INT-CALCULATION.json}), and {@code RATE-ROUND-PAYABLE}, source-checked.
     */
    private static ComputedTax interestCharged(List<String> lawAndExec) {
        List<ComputedTax.Step> trace = List.of(
                new ComputedTax.Step(1, "interest", "INT-CALCULATION", "INTEREST", "", Optional.empty(),
                        Optional.of(Money.of("1200")), "Interest for paying late."),
                new ComputedTax.Step(2, "round", "RATE-ROUND-PAYABLE", "ROUNDING", "", Optional.of(Money.of("10004")),
                        Optional.of(Money.of("10000")), "Rounded."));
        return new ComputedTax("id", "WL-2025-26-TEST", "2025-26", "FIRST_SCHEDULE_RATES", Money.of("900000"),
                Money.of("8800"), Money.ZERO, new ComputedTax.AdvanceTax(false, Money.ZERO, List.of(), Optional.empty()),
                List.of(), List.of(), Money.of("10000"), "fingerprint", lawAndExec, trace, List.of(), List.of());
    }

    /** The year's law and exec pins as a computation made today stores them (W-138). */
    private static List<String> lawAndExec2025() {
        return CALCULATOR.packVersions(TaxYearId.of(2025)).orElseThrow();
    }

    private static Map<String, String> pinnedFromLock() throws IOException {
        JsonNode rules = lock("2025-26").get("rules");
        return Map.of("INT-CALCULATION", rules.get("INT-CALCULATION").asString(), "RATE-ROUND-PAYABLE",
                rules.get("RATE-ROUND-PAYABLE").asString());
    }

    @Test
    void model_whenALineReadsAnUnverifiedRuleAsItStillIs_marksThatLineOnly() throws IOException {
        // When
        ComputationReport report = SERVICE.model(view(interestCharged(lawAndExec2025())), pinnedFromLock());

        // Then
        assertThat(lineOf(report, "INT-CALCULATION").mark()).contains("Not verified");
        assertThat(lineOf(report, "RATE-ROUND-PAYABLE").mark()).isEmpty();
        assertThat(report.packNote()).isEmpty();
    }

    @Test
    void model_whenTheRuleHasChangedOrWasNotPinned_claimsNoStatus() throws IOException {
        // Given — the unverified rule's checksum altered: computed under another text of it
        Map<String, String> altered = new HashMap<>(pinnedFromLock());
        altered.put("INT-CALCULATION", "sha256:another-text");

        // When
        ComputationReport changed = SERVICE.model(view(interestCharged(lawAndExec2025())), altered);
        ComputationReport unpinned = SERVICE.model(view(interestCharged(lawAndExec2025())), Map.of());

        // Then
        assertThat(lineOf(changed, "INT-CALCULATION").mark()).hasValueSatisfying(mark -> assertThat(mark)
                .startsWith("This rule has changed since this computation"));
        assertThat(lineOf(changed, "RATE-ROUND-PAYABLE").mark()).isEmpty();
        assertThat(lineOf(unpinned, "INT-CALCULATION").mark()).hasValueSatisfying(mark -> assertThat(mark)
                .startsWith("This computation did not record which text"));
    }

    @Test
    void model_whenTheRuleDataHasMovedOnSinceTheComputation_saysSoAndKeepsTheStoredFigures() throws IOException {
        // Given — pinned to an earlier law layer than the lock's
        String law = lock("2025-26").at("/layers/law/version").asString();
        List<String> earlier = lawAndExec2025().stream().map(pin -> pin.replace(law, "2025-26.law.1")).toList();

        // When
        ComputationReport report = SERVICE.model(view(interestCharged(earlier)), pinnedFromLock());

        // Then
        assertThat(report.packNote()).hasValueSatisfying(note -> assertThat(note)
                .contains("2025-26.law.1", law).contains("as they were computed"));
        assertThat(report.header().finalAmount()).isEqualTo("₹10,000");
        assertThat(lineOf(report, "INT-CALCULATION").figure()).isEqualTo("₹1,200");
    }

    private static ComputationReport.Line lineOf(ComputationReport report, String rule) {
        return report.sections().stream().flatMap(section -> section.lines().stream())
                .filter(line -> line.rule().equals(rule)).findFirst().orElseThrow();
    }

    @Test
    void template_whenRead_printsNoTextOfItsOwn() throws IOException {
        // Given — O-20: every word is a model field or a content sentence
        String template;
        try (InputStream in = getClass().getResourceAsStream("/templates/taxplanning/computation-report.html")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String body = template.substring(template.indexOf("<body>"));

        // When — the text between tags, once comments and whitespace are gone
        Set<String> text = Arrays.stream(body.replaceAll("<!--.*?-->", "").split("<[^>]*>"))
                .map(String::strip).filter(fragment -> !fragment.isEmpty()).collect(Collectors.toSet());

        // Then
        assertThat(text).isEmpty();
    }
}
