package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.InstrumentCitations;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.BaseFont;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.util.XRRuntimeException;

/**
 * One stored computation as a PDF, made when asked for and kept nowhere (PRD F10; W-123 C2a, W-123a). The figures and
 * sentences are the stored ones; the pack is read only to say what the stored figures cannot: whether a line's rule is
 * verified, guarded by the rule's checksum then and now (C1a), and whether the rule data has moved on (C2a).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComputationReportService {

    /** Noto Sans, SIL Open Font License 1.1 (W-123a): the PDF's own fonts lack the rupee sign the sentences print. */
    private static final List<String> FONTS = List.of("fonts/NotoSans-Regular.ttf", "fonts/NotoSans-Bold.ttf");

    private static final String NOT_VERIFIED = "UNVERIFIED";

    private final DraftComputationService computationService;
    private final DraftComputationRepository repository;
    private final TaxCalculator taxCalculator;
    private final TemplateEngine templateEngine;

    /**
     * @throws IllegalArgumentException if the account holds no such receipt, or the draft or its profile is not this
     *     account's or is held for erasure
     */
    public byte[] report(String email, String receipt) {
        DraftComputationView view = computationService.byReceipt(email, receipt);
        // byReceipt has found it; a computation stored before W-138 kept no checksums, so none is pinned
        Map<String, String> pinned = repository.findByReceiptAndEmail(receipt, email)
                .orElseThrow(() -> new IllegalStateException("Receipt " + receipt + " was read and is now gone"))
                .getRuleChecksums();
        return render(model(view, pinned == null ? Map.of() : pinned));
    }

    /** The report of {@code view}, whose rules were read at {@code pinned}; separate so it is tested on the engine alone. */
    ComputationReport model(DraftComputationView view, Map<String, String> pinned) {
        ComputedTax tax = view.tax();
        TaxYearId year = yearOf(tax.provenance().taxYear());
        Map<String, String> words = taxCalculator.reportSentences(year);
        Set<String> rules = tax.workings().trace().stream().map(ComputedTax.Step::rule).collect(Collectors.toSet());
        LineMarks marks = new LineMarks(pinned, taxCalculator.ruleChecksums(year, rules),
                taxCalculator.verificationLevels(year, rules), words);
        Map<String, List<ComputationReport.Line>> byKind = new LinkedHashMap<>();
        tax.workings().trace().forEach(step -> byKind.computeIfAbsent(step.kind(), kind -> new ArrayList<>())
                .add(new ComputationReport.Line(step.sequence(), step.explanation(), step.rule(),
                        step.out().map(Money::inIndianNotation).orElse(""), marks.of(step.rule()))));
        List<ComputationReport.Section> sections = byKind.entrySet().stream()
                .map(kind -> new ComputationReport.Section(word(words, "KIND_" + kind.getKey()), kind.getValue()))
                .toList();
        return new ComputationReport(header(tax, words), packNote(tax, year, words), coarseNote(view, words),
                new ComputationReport.Columns(word(words, "COLUMN_STEP"), word(words, "COLUMN_RULE"),
                        word(words, "COLUMN_FIGURE")),
                sections, disclaimer(tax, year, words));
    }

    private static ComputationReport.Header header(ComputedTax tax, Map<String, String> words) {
        return new ComputationReport.Header(word(words, "TITLE"),
                word(words, "RECEIPT").replace("{receipt}", tax.provenance().receipt()),
                word(words, "TAX_YEAR").replace("{taxYear}", tax.provenance().taxYear()),
                word(words, "TOTAL_INCOME"), tax.figures().totalIncome().inIndianNotation(),
                word(words, "FINAL_AMOUNT"), tax.figures().finalAmount().inIndianNotation());
    }

    /** Only a view that said it was coarse is reported as coarse; one that said nothing says nothing here (W-122b1 C1a). */
    private static Optional<String> coarseNote(DraftComputationView view, Map<String, String> words) {
        if (!view.coarse().orElse(false)) {
            return Optional.empty();
        }
        return view.band().map(band -> word(words, "COARSE_BAND").replace("{lower}", band.lower().inIndianNotation())
                .replace("{upper}", band.upper().inIndianNotation()));
    }

    private Optional<String> packNote(ComputedTax tax, TaxYearId year, Map<String, String> words) {
        List<String> current = taxCalculator.packVersions(year).orElseThrow(
                () -> new IllegalArgumentException("No rule pack ships for " + year.label()));
        if (current.equals(tax.provenance().lawAndExec())) {
            return Optional.empty();
        }
        return Optional.of(word(words, "PACK_UPDATED_SINCE")
                .replace("{computedUnder}", String.join(", ", tax.provenance().lawAndExec()))
                .replace("{current}", String.join(", ", current)));
    }

    private String disclaimer(ComputedTax tax, TaxYearId year, Map<String, String> words) {
        InstrumentCitations instruments = taxCalculator.instrumentCitations(year);
        return word(words, "DISCLAIMER").replace("{act}", instruments.act())
                .replace("{financeAct}", instruments.financeAct()).replace("{rules}", instruments.rules())
                .replace("{taxYear}", tax.provenance().taxYear());
    }

    /** @throws IllegalStateException if the year's content lacks a sentence the report prints */
    static String word(Map<String, String> words, String code) {
        String word = words.get(code);
        if (word == null) {
            throw new IllegalStateException("The year's content has no report." + code);
        }
        return word;
    }

    private static TaxYearId yearOf(String label) {
        TaxYearId year = TaxYearId.of(Integer.parseInt(label.substring(0, 4)));
        if (!year.label().equals(label)) {
            throw new IllegalStateException("A stored computation names tax year " + label + ", which is not a year label");
        }
        return year;
    }

    byte[] render(ComputationReport report) {
        Context context = new Context();
        context.setVariable("report", report);
        String html = templateEngine.process("taxplanning/computation-report", context);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ITextRenderer renderer = new ITextRenderer();
            for (String font : FONTS) {
                renderer.getFontResolver().addFont(font, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
        } catch (XRRuntimeException | DocumentException | IOException e) {
            throw new IllegalStateException("The computation report could not be rendered as a PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /**
     * What a line's rule lets the report say about it (C1a): a status only where the rule's text is the one the
     * computation read — the checksum pinned then equals today's.
     */
    private record LineMarks(Map<String, String> pinned, Map<String, String> current, Map<String, Set<String>> levels,
            Map<String, String> words) {

        Optional<String> of(String rule) {
            String then = pinned.get(rule);
            if (then == null) {
                return Optional.of(word(words, "RULE_NOT_RECORDED"));
            }
            if (!then.equals(current.get(rule))) {
                return Optional.of(word(words, "RULE_CHANGED_SINCE"));
            }
            return levels.getOrDefault(rule, Set.of()).contains(NOT_VERIFIED)
                    ? Optional.of(word(words, "NOT_VERIFIED")) : Optional.empty();
        }
    }
}
