package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's claim integrity, computed on every request and stored nowhere (W-119c): the draft run as it stands, with
 * every evidenced figure cut to what is held, and with each kind of evidence held to nothing (owner's ruling at build,
 * session 99). A rule whose lines move when a kind is held to nothing is a claim that kind bears on — no list of rules is
 * kept, so a rule added to the pack later is covered with no change here. A rent row says whether the landlord's
 * particulars are on file only where the engine asked for something on its rule (owner's ruling, W-119d): the threshold
 * is the engine's to know.
 */
@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
@RequiredArgsConstructor
public class DraftClaimIntegrityService {

    /** Stated with every panel beyond what every computation states (W-119c C3a; owner's rulings at build). */
    private static final List<String> NOT_INCLUDED = List.of(
            "Rent is taken as equal each month: a tenancy's rent is cut in proportion to its receipted months (W-119c C3a)",
            "A gap can be the effect of another claim's missing evidence: less rent can move a limit elsewhere",
            "The landlord's particulars are said for the draft's landlords together: a tenancy does not say which is its own");

    /** The kinds of line a claim is (W-119c): the engine's {@code StepKind} names, as {@code ComputedTax} gives them. */
    private static final Set<String> CLAIM_KINDS = Set.of("EXEMPTION", "DEDUCTION");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final ParticularsOnFile particularsOnFile;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, states no due date, or elects a regime the
     *     year does not have
     */
    public DraftClaimIntegrity claimIntegrity(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        EngineInputs inputs = assembled.inputs();
        return new DraftClaimIntegrity(today, inputs.regime().value(), assembled.draft().getCurrentRevision(),
                rows(inputs, owned.inputs().evidence(), particularsOnFile.landlords(owned.inputs().thirdParties().landlordIds())),
                Stream.concat(DraftComputationService.NOT_INCLUDED.stream(), NOT_INCLUDED.stream()).toList());
    }

    /** The rows, by rule id; separate from {@link #claimIntegrity} so it is tested on the engine alone. */
    List<DraftClaimIntegrity.Row> rows(EngineInputs inputs, EvidenceHeld held, boolean landlordsOnFile) {
        EvidencedFacts facts = new EvidencedFacts(inputs.entered(), held, inputs.taxYear());
        ComputedTax standing = taxCalculator.compute(inputs);
        Map<String, Money> asItStands = byRule(standing);
        Set<String> asked = new TreeSet<>();
        standing.workings().notices().forEach(notice -> asked.addAll(notice.relatedRules()));
        ComputedTax supported = taxCalculator.compute(with(inputs, facts.supported()));
        Map<String, Money> evidenced = byRule(supported);
        Map<String, Set<EvidenceKind>> bearsOn = new TreeMap<>();
        for (EvidenceKind kind : facts.kindsEntered()) {
            Map<String, Money> nothingHeld = byRule(inputs, facts.nothingHeldFor(kind));
            Set<String> rules = new TreeSet<>(asItStands.keySet());
            rules.addAll(nothingHeld.keySet());
            rules.stream().filter(rule -> !amount(asItStands, rule).equals(amount(nothingHeld, rule)))
                    .forEach(rule -> bearsOn.computeIfAbsent(rule, ignored -> new TreeSet<>()).add(kind));
        }
        Sentences sentences = new Sentences(taxCalculator.evidenceSentences(inputs.taxYear()));
        List<DraftClaimIntegrity.Row> rows = new ArrayList<>();
        bearsOn.forEach((rule, kinds) -> {
            Money allowed = amount(asItStands, rule);
            Money supportedAmount = amount(evidenced, rule);
            Money gap = allowed.minus(supportedAmount);
            rows.add(new DraftClaimIntegrity.Row(rule, allowed, supportedAmount, gap, limitedBy(supported, rule),
                    says(gap, kinds, facts, asked.contains(rule) ? Optional.of(landlordsOnFile) : Optional.empty(),
                            sentences)));
        });
        return List.copyOf(rows);
    }

    /**
     * @param landlordsOnFile whether the draft's landlords' particulars are on file, where the engine asked for something
     *     on the row's rule (r.205(2) asks only above its threshold, which the engine knows and this does not)
     */
    private static List<String> says(Money gap, Set<EvidenceKind> kinds, EvidencedFacts facts,
            Optional<Boolean> landlordsOnFile, Sentences sentences) {
        List<String> says = new ArrayList<>();
        if (gap.equals(Money.ZERO)) {
            says.add(sentences.of("FULLY_EVIDENCED"));
        } else if (gap.isGreaterThan(Money.ZERO)) {
            says.add(sentences.of("NOT_YET_EVIDENCED").replace("{amount}", gap.inIndianNotation()));
            Map<EvidenceKind, String> closes = new EnumMap<>(EvidenceKind.class);
            closes.put(EvidenceKind.RENT_RECEIPTS, Long.toString(facts.monthsNotReceipted()));
            closes.put(EvidenceKind.JOURNEY_EXPENDITURE, facts.travelNotEvidenced().inIndianNotation());
            closes.put(EvidenceKind.QUALIFYING_PAYMENT, facts.paymentsNotEvidenced().inIndianNotation());
            kinds.stream().filter(ChapterViiiCuts::isChapterViii).forEach(kind ->
                    closes.put(kind, facts.chapterViiiNotEvidenced(kind).inIndianNotation()));
            kinds.forEach(kind -> says.add(sentences.of(kind.name()).replace("{months}", closes.get(kind))
                    .replace("{amount}", closes.get(kind))));
        } else {
            says.add(sentences.of("RISES_WITH_LESS_EVIDENCE"));
        }
        if (kinds.contains(EvidenceKind.RENT_RECEIPTS)) {
            landlordsOnFile.ifPresent(onFile -> says.add(sentences.of(onFile ? "LANDLORD_ON_FILE" : "LANDLORD_NOT_ON_FILE")));
        }
        return says;
    }

    /** The year's sentences; one the content does not word is a pack this cannot speak for. */
    private record Sentences(Map<String, String> byCode) {

        String of(String code) {
            String sentence = byCode.get(code);
            if (sentence == null) {
                throw new IllegalStateException("The year's content has no evidence sentence " + code);
            }
            return sentence;
        }
    }

    private Map<String, Money> byRule(EngineInputs inputs, EnteredFacts entered) {
        return byRule(taxCalculator.compute(with(inputs, entered)));
    }

    /**
     * Each claim's lines added up: an exemption or a deduction, by the kind the engine gives the line. A tax, a rounding
     * or an instalment moves with every figure above it and is no claim.
     */
    private static Map<String, Money> byRule(ComputedTax computed) {
        Map<String, Money> byRule = new TreeMap<>();
        for (ComputedTax.Step step : computed.workings().trace()) {
            if (CLAIM_KINDS.contains(step.kind())) {
                step.out().ifPresent(out -> byRule.merge(step.rule(), out, Money::plus));
            }
        }
        return byRule;
    }

    private static Money amount(Map<String, Money> byRule, String rule) {
        return byRule.getOrDefault(rule, Money.ZERO);
    }

    private static Optional<String> limitedBy(ComputedTax computed, String rule) {
        return computed.workings().trace().stream().filter(step -> step.rule().equals(rule))
                .map(ComputedTax.Step::limitedBy).filter(limit -> limit != null && !limit.isBlank()).findFirst();
    }

    private static EngineInputs with(EngineInputs inputs, EnteredFacts entered) {
        return new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(), entered, inputs.assessee(),
                inputs.family(), inputs.ledgers(), inputs.elections(), inputs.capitalGains(), inputs.filing());
    }
}
