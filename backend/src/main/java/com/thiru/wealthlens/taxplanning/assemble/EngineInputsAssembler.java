package com.thiru.wealthlens.taxplanning.assemble;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.input.RegimeElectionSnapshot;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionState;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxplanning.portfolio.CapitalGainsAdapter;
import com.thiru.wealthlens.taxplanning.profile.AssesseeKind;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileService;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistoryRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The only place {@link EngineInputs} is built ({@code DR 23} A3): a draft, its profile, family, elections and, for the profile
 * that holds the account's portfolio, its capital gains. Here, and not in the engine, the date is read. What earlier years
 * carried in is what the draft states (W-234).
 */
@Component
@RequiredArgsConstructor
public class EngineInputsAssembler {

    private final DraftService draftService;
    private final TaxProfileService taxProfileService;
    private final FamilyMemberRepository familyMemberRepository;
    private final RegimeElectionHistoryRepository electionHistoryRepository;
    private final CapitalGainsAdapter capitalGainsAdapter;

    /**
     * The inputs for this draft.
     *
     * @param regime the regime to compute under
     * @throws IllegalArgumentException if the draft is not this account's or is not ready to compute
     */
    public Assembled assemble(String email, String draftId, RegimeCode regime, LocalDate asOf) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        FilingDraft draft = owned.draft();
        TaxYearId year = TaxYearId.of(draft.getTaxYearStart());
        TaxpayerProfile profile = taxProfileService.owned(email, draft.getProfileId());
        CapitalGainsInput gains = profile.isHoldsPortfolio() ? capitalGainsAdapter.inputFor(email, year)
                : CapitalGainsInput.none(asOf.atStartOfDay(ZoneOffset.UTC).toInstant(), "not-the-portfolio-holder");
        EngineInputs inputs = new EngineInputs(year, regime, TemporalContext.forTaxYear(year, asOf), owned.inputs().entered(),
                assessee(profile), family(profile), owned.inputs().brought(), elections(profile, year), gains,
                owned.inputs().filing().toFilingFacts());
        return new Assembled(inputs, draft, profile.isHoldsPortfolio());
    }

    /**
     * Inputs for reading a holder's capital gains alone: no entries, no filing, and the facts the rows may read about the person.
     * No figure from them but the head's is meaningful, and nothing they make is stored.
     */
    public EngineInputs forCapitalGains(TaxpayerProfile profile, TaxYearId year, RegimeCode regime, LocalDate asOf,
            CapitalGainsInput gains) {
        return new EngineInputs(year, regime, TemporalContext.forTaxYear(year, asOf), EnteredFacts.none(), assessee(profile),
                family(profile), LedgerSnapshot.empty(), elections(profile, year), gains, FilingFacts.notYetFurnished(year.endsOn()));
    }

    /**
     * The inputs and what they came from.
     *
     * @param capitalGainsIncluded whether the portfolio's gains were read
     */
    public record Assembled(EngineInputs inputs, FilingDraft draft, boolean capitalGainsIncluded) {
    }

    private static Assessee assessee(TaxpayerProfile profile) {
        if (profile.getKind() == AssesseeKind.HUF) {
            return new Huf(profile.getResidentialStatus());
        }
        return new Individual(profile.getDateOfBirth(), profile.getResidentialStatus(),
                Optional.ofNullable(profile.getDisability()), Optional.ofNullable(profile.getDateOfDeath()));
    }

    private FamilySnapshot family(TaxpayerProfile profile) {
        List<FamilySnapshot.Member> members = familyMemberRepository.findByProfileId(profile.getId()).stream()
                .map(EngineInputsAssembler::member).toList();
        return new FamilySnapshot(members);
    }

    private static FamilySnapshot.Member member(FamilyMember member) {
        return new FamilySnapshot.Member(member.getRelationship(), member.getDateOfBirth(), member.isDependant(),
                Optional.ofNullable(member.getDisability()), Optional.ofNullable(member.getDateOfDeath()),
                member.isMultipleBirthAfterFirstChild(), member.isResidentInIndia());
    }

    private RegimeElectionSnapshot elections(TaxpayerProfile profile, TaxYearId year) {
        Map<RegimeCode, ElectionState> states = new HashMap<>();
        for (RegimeElectionHistory history : electionHistoryRepository.findByProfileId(profile.getId())) {
            if (history.isElectedBy(year)) {
                states.put(new RegimeCode(history.getRegimeCode()), ElectionState.IN_EFFECT);
            }
        }
        return new RegimeElectionSnapshot(states, Map.of());
    }
}
