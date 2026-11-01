package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Computes a draft and keeps what was shown, by receipt (W-104, W-107, W-109; D-442). */
@Service
@Transactional
@RequiredArgsConstructor
public class DraftComputationService {

    /** Stated with every figure until each is built (D-442, D-445): W-089a, W-088i2, W-088i3 and the pin in {@code DR 11} F4.1. */
    private static final List<String> NOT_INCLUDED = List.of(
            "Relief for salary received in arrears or in advance",
            "A house-reinvestment exemption by deposit in the capital gains account scheme",
            "A house-reinvestment exemption charged back in a later year when the new house is sold or another is bought",
            "Capital gains are read from the portfolio as it stands now, not pinned to this draft");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final DraftService draftService;
    private final DraftComputationRepository repository;

    /** Computes the draft under {@code regimeCode} and stores the result. */
    public DraftComputationView compute(String email, String draftId, String regimeCode) {
        if (regimeCode == null || regimeCode.isBlank()) {
            throw new BadRequestException("A computation needs a regime");
        }
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId, new RegimeCode(regimeCode), LocalDate.now());
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
        repository.save(stored);
        return view;
    }

    /** Every regime priced on the draft; the draft is unchanged and nothing is stored. */
    @Transactional(readOnly = true)
    public RegimeComparison compare(String email, String draftId) {
        int year = draftService.ownedDraft(email, draftId).draft().getTaxYearStart();
        RegimeCode regime = taxCalculator.defaultRegime(TaxYearId.of(year));
        return taxCalculator.compareRegimes(assembler.assemble(email, draftId, regime, LocalDate.now()).inputs());
    }

    /**
     * What a receipt showed.
     *
     * @throws BadRequestException if this account holds no such receipt
     */
    @Transactional(readOnly = true)
    public DraftComputationView byReceipt(String email, String receipt) {
        return repository.findByReceiptAndEmail(receipt, email)
                .map(stored -> MAPPER.readValue(stored.getResultJson(), DraftComputationView.class))
                .orElseThrow(() -> new BadRequestException("This account holds no such receipt"));
    }
}
