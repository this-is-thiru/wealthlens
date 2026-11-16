package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.ItrFormChoice;
import com.thiru.wealthlens.taxengine.api.Optimisation;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.protection.TaxAccessLog;
import com.thiru.wealthlens.taxplanning.workspace.DraftInputs;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
     * a dividend without its received date (W-112a relieves only dated ones), and what no draft can state (interest on an
     * excess refund, W-112b; an Assessing Officer's order; the fees for a revised return or a report).
     */
    private static final String COARSE = "COARSE";

    static final List<String> NOT_INCLUDED = List.of(
            "Relief for salary received in arrears or in advance",
            "Relief from interest for deferring advance tax on a dividend entered without the date it was received",
            "Interest charged on a refund later found not to be due, which only a regular assessment decides",
            "Advance tax or interest under an Assessing Officer's order",
            "Fees for a late revised return, a late audit report or a late accountant's report",
            "A house-reinvestment exemption by deposit in the capital gains account scheme",
            "A house-reinvestment exemption charged back in a later year when the new house is sold or another is bought",
            "Capital gains are read from the portfolio as it stands now, not pinned to this draft");

    /** Stated beside {@link #NOT_INCLUDED} while a figure is coarse (W-122b C3a, C4a; D-720). */
    static final List<String> BAND_NOT_INCLUDED = List.of(
            "Income not yet entered is outside the band: it would raise the tax above it (W-324)",
            "The band narrows as steps are answered in order; entering income while pay structure, rent or investments are"
                    + " still open can widen it");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final DraftService draftService;
    private final DraftComputationRepository repository;
    private final Clock clock;
    private final TaxAccessLog taxAccessLog;
    private final OptimiserSettings optimiserSettings;
    private final HrSupportTable hrSupportTable;

    /** Computes the draft under {@code regimeCode} and stores the result. */
    public DraftComputationView compute(String email, String draftId, String regimeCode) {
        if (regimeCode == null || regimeCode.isBlank()) {
            throw new BadRequestException("A computation needs a regime");
        }
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId, new RegimeCode(regimeCode), LocalDate.now(clock));
        ComputedTax tax = taxCalculator.compute(assembled.inputs());
        Set<OnboardingStep> answered = DraftInputs.of(assembled.draft()).onboarding().answered();
        Optional<Band> band = band(assembled.inputs(), tax, answered);
        DraftComputationView view = new DraftComputationView(tax, draftId, assembled.draft().getCurrentRevision(),
                assembled.capitalGainsIncluded(), Optional.of(band.isPresent()), band, band.isPresent()
                        ? Stream.concat(NOT_INCLUDED.stream(), BAND_NOT_INCLUDED.stream()).toList() : NOT_INCLUDED);
        DraftComputation stored = new DraftComputation();
        stored.setId(UUID.randomUUID().toString());
        stored.setReceipt(tax.receipt());
        stored.setEmail(email);
        stored.setDraftId(draftId);
        stored.setRevision(view.revision());
        stored.setComputedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        stored.setResultJson(MAPPER.writeValueAsString(view));
        stored.setLawAndExec(tax.lawAndExec());
        stored.setRuleChecksums(taxCalculator.ruleChecksums(TaxYearId.of(assembled.draft().getTaxYearStart()), rulesRead(tax)));
        repository.save(stored);
        draftService.clearStaleness(draftId);
        return view;
    }

    /**
     * The band while any step is open, empty once all five are answered (W-122b C3a, C5a); separate from
     * {@link #compute} so it is tested on the engine alone.
     */
    Optional<Band> band(EngineInputs inputs, ComputedTax tax, Set<OnboardingStep> answered) {
        if (answered.containsAll(EnumSet.allOf(OnboardingStep.class))) {
            return Optional.empty();
        }
        return Optional.of(new Band(tax.bill(), taxCalculator.billWithRoomsFilled(inputs,
                DraftOnboardingService.openFamilies(answered)), coarseSentence(inputs.taxYear())));
    }

    private String coarseSentence(TaxYearId year) {
        String says = taxCalculator.computationSentences(year).get(COARSE);
        if (says == null) {
            throw new IllegalStateException("The year's content has no computation." + COARSE);
        }
        return says;
    }

    private static Set<String> rulesRead(ComputedTax tax) {
        return tax.trace().stream().map(ComputedTax.Step::rule).filter(rule -> rule != null && !rule.isBlank())
                .collect(Collectors.toSet());
    }

    /** Every regime priced on the draft, labelled coarse while a step is open; the draft is unchanged and nothing is stored. */
    @Transactional(readOnly = true)
    public DraftRegimeComparison compare(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        TaxYearId year = TaxYearId.of(owned.draft().getTaxYearStart());
        RegimeComparison comparison = taxCalculator.compareRegimes(
                assembler.assemble(email, draftId, taxCalculator.defaultRegime(year), LocalDate.now(clock)).inputs());
        boolean coarse = !owned.inputs().onboarding().answered().containsAll(EnumSet.allOf(OnboardingStep.class));
        return new DraftRegimeComparison(comparison.chosen(), comparison.mode(), comparison.priced(), coarse,
                coarse ? Optional.of(coarseSentence(year)) : Optional.empty());
    }

    /**
     * The form the draft's return of {@code returnKind} is furnished in under {@code regimeCode}, with every form's
     * verdict (W-091, D-653 C2a); the draft is unchanged and nothing is stored.
     *
     * @throws IllegalArgumentException if either is missing, the draft is not this account's, or no form of the year
     *     serves the kind
     */
    @Transactional(readOnly = true)
    public ItrFormChoice chooseForm(String email, String draftId, String regimeCode, String returnKind) {
        if (regimeCode == null || regimeCode.isBlank()) {
            throw new BadRequestException("A form choice needs a regime, since total income depends on it");
        }
        if (returnKind == null || returnKind.isBlank()) {
            throw new BadRequestException("A form choice needs a returnKind, the provision the return is furnished under,"
                    + " such as S263_1");
        }
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId, new RegimeCode(regimeCode),
                LocalDate.now(clock));
        return taxCalculator.chooseForm(assembled.inputs(), new ReturnKind(returnKind));
    }

    /**
     * The split of the draft's movable pay that leaves the least tax, the regime chosen on each (W-238b); the draft is
     * unchanged and nothing is stored. Each move carries how likely the employer is to agree to it (W-238f).
     *
     * @throws IllegalArgumentException if the draft is not this account's, the request asks for more breadth than the
     *     setting, or the request does not fit the draft or the year's pack
     */
    @Transactional(readOnly = true)
    public DraftOptimisation optimise(String email, String draftId, DraftOptimisationRequest request) {
        int year = draftService.ownedDraft(email, draftId).draft().getTaxYearStart();
        int breadth = optimiserSettings.breadthFor(request.verificationBreadth());
        RegimeCode regime = taxCalculator.defaultRegime(TaxYearId.of(year));
        Optimisation optimisation = taxCalculator.optimise(new AllocationSpace(
                assembler.assemble(email, draftId, regime, LocalDate.now(clock)).inputs(), request.moves(),
                request.residualCode(), request.cashFloorRupees(), breadth,
                request.providentFundTerms()));
        return DraftOptimisation.of(optimisation, hrSupportTable);
    }

    /**
     * Every computation of a profile, newest first, each read recorded (W-123b C1a): the receipts a person takes each
     * report by before asking for the profile to be erased.
     *
     * @throws BadRequestException if the profile is not this account's, or is held for erasure
     */
    @Transactional(readOnly = true)
    public List<ComputationListing> computations(String email, String profileId) {
        Map<String, FilingDraft> drafts = draftService.profileDrafts(email, profileId).stream()
                .collect(Collectors.toMap(FilingDraft::getId, Function.identity()));
        return repository.findByDraftIdIn(drafts.keySet()).stream()
                .sorted(Comparator.comparing(DraftComputation::getComputedAt,
                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(DraftComputation::getReceipt))
                .map(stored -> listing(email, stored, drafts.get(stored.getDraftId())))
                .toList();
    }

    private ComputationListing listing(String email, DraftComputation stored, FilingDraft draft) {
        ComputedTax tax = MAPPER.readValue(stored.getResultJson(), DraftComputationView.class).tax();
        taxAccessLog.record(email, ReadSubject.COMPUTATION, stored.getReceipt());
        return new ComputationListing(stored.getReceipt(), tax.taxYear(), draft.getLabel(), stored.getRevision(),
                tax.regime(), Optional.ofNullable(stored.getComputedAt()));
    }

    /**
     * What a receipt showed.
     *
     * @throws BadRequestException if this account holds no such receipt, or its profile is held for erasure
     */
    public DraftComputationView byReceipt(String email, String receipt) {
        DraftComputation stored = repository.findByReceiptAndEmail(receipt, email)
                .orElseThrow(() -> new BadRequestException("This account holds no such receipt"));
        draftService.ownedDraft(email, stored.getDraftId());
        taxAccessLog.record(email, ReadSubject.COMPUTATION, receipt);
        return MAPPER.readValue(stored.getResultJson(), DraftComputationView.class);
    }
}
