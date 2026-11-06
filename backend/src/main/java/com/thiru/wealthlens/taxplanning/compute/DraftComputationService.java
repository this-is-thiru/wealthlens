package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Computes a draft and keeps what was shown, by receipt (W-104, W-107, W-109; D-442). */
@Service
@Transactional
@RequiredArgsConstructor
public class DraftComputationService {

    /**
     * Stated with every figure until each is built (D-442, D-445, D-446): W-089a, W-088i2, W-088i3, the pin in {@code DR 11} F4.1,
     * W-112a, W-112b, and what no draft can state (an Assessing Officer's order, the fees for a revised return or a report).
     */
    private static final List<String> NOT_INCLUDED = List.of(
            "Relief for salary received in arrears or in advance",
            "Relief from interest for deferring advance tax where the shortfall came from capital gains or dividends",
            "Interest on a refund, either way: paid to you on a refund due, or charged on a refund that was too much",
            "Advance tax or interest under an Assessing Officer's order",
            "Fees for a late revised return, a late audit report or a late accountant's report",
            "A house-reinvestment exemption by deposit in the capital gains account scheme",
            "A house-reinvestment exemption charged back in a later year when the new house is sold or another is bought",
            "Capital gains are read from the portfolio as it stands now, not pinned to this draft");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final DraftService draftService;
    private final DraftComputationRepository repository;
    private final Clock clock;
    private final TaxAccessLog taxAccessLog;

    /** Computes the draft under {@code regimeCode} and stores the result. */
    public DraftComputationView compute(String email, String draftId, String regimeCode) {
        if (regimeCode == null || regimeCode.isBlank()) {
            throw new BadRequestException("A computation needs a regime");
        }
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId, new RegimeCode(regimeCode), LocalDate.now(clock));
        ComputedTax tax = taxCalculator.compute(assembled.inputs());
        DraftComputationView view = new DraftComputationView(tax, draftId, assembled.draft().getCurrentRevision(),
                assembled.capitalGainsIncluded(), NOT_INCLUDED);
        DraftComputation stored = new DraftComputation();
        stored.setId(UUID.randomUUID().toString());
        stored.setReceipt(tax.receipt());
        stored.setEmail(email);
        stored.setDraftId(draftId);
        stored.setRevision(view.revision());
        stored.setResultJson(MAPPER.writeValueAsString(view));
        stored.setLawAndExec(tax.lawAndExec());
        stored.setRuleChecksums(taxCalculator.ruleChecksums(TaxYearId.of(assembled.draft().getTaxYearStart()), rulesRead(tax)));
        repository.save(stored);
        draftService.clearStaleness(draftId);
        return view;
    }

    private static Set<String> rulesRead(ComputedTax tax) {
        return tax.trace().stream().map(ComputedTax.Step::rule).filter(rule -> rule != null && !rule.isBlank())
                .collect(Collectors.toSet());
    }

    /** Every regime priced on the draft; the draft is unchanged and nothing is stored. */
    @Transactional(readOnly = true)
    public RegimeComparison compare(String email, String draftId) {
        int year = draftService.ownedDraft(email, draftId).draft().getTaxYearStart();
        RegimeCode regime = taxCalculator.defaultRegime(TaxYearId.of(year));
        return taxCalculator.compareRegimes(assembler.assemble(email, draftId, regime, LocalDate.now(clock)).inputs());
    }

    /**
     * What a receipt showed.
     *
     * @throws BadRequestException if this account holds no such receipt
     */
    public DraftComputationView byReceipt(String email, String receipt) {
        DraftComputation stored = repository.findByReceiptAndEmail(receipt, email)
                .orElseThrow(() -> new BadRequestException("This account holds no such receipt"));
        taxAccessLog.record(email, ReadSubject.COMPUTATION, receipt);
        return MAPPER.readValue(stored.getResultJson(), DraftComputationView.class);
    }
}
