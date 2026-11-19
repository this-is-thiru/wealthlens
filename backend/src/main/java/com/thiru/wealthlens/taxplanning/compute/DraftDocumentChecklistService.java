package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ClaimPlaybook;
import com.thiru.wealthlens.taxengine.api.PricedClaim;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.HousingLoan;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.assemble.EngineInputsAssembler;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist.Document;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist.Outstanding;
import com.thiru.wealthlens.taxplanning.compute.DraftDocumentChecklist.State;
import com.thiru.wealthlens.taxplanning.workspace.DraftInputs;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.EvidenceHeld;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A draft's documentation checklist, computed on every request and stored nowhere (W-120): the claims the engine prices
 * as made, each catalogue document with the record the catalogue names for it read from the draft's evidence, its
 * profile or the engine's facts. Which record answers a document is catalogue data, so a claim catalogued later is
 * covered with no change here; the landlord's particulars are asked only where the engine raised r.205(2)'s notice.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DraftDocumentChecklistService {

    /** Stated with every checklist beyond what every computation states (W-120 C4a; D-718). */
    static final List<String> NOT_INCLUDED = List.of(
            "Only claims in the claim catalogue list their documents; a deduction or exemption not yet catalogued lists none",
            "Payments under every qualifying payment code are evidenced together: own pension and investment share one figure",
            "Rent receipts are counted over every tenancy together, and landlords and lenders are on file for the draft together",
            "Conditions that are not documents, such as how a payment was made, are applied in the computation itself");

    private final DraftService draftService;
    private final EngineInputsAssembler assembler;
    private final TaxCalculator taxCalculator;
    private final ParticularsOnFile particularsOnFile;
    private final Clock clock;

    /**
     * @throws IllegalArgumentException if the draft is not this account's, states no due date, or elects a regime the
     *     year does not have
     */
    public DraftDocumentChecklist checklist(String email, String draftId) {
        DraftService.OwnedDraft owned = draftService.ownedDraft(email, draftId);
        LocalDate today = LocalDate.now(clock);
        EngineInputsAssembler.Assembled assembled = assembler.assemble(email, draftId,
                DraftForecastService.regimeOf(owned.draft(), taxCalculator), today);
        EngineInputs inputs = assembled.inputs();
        DraftInputs.ThirdParties parties = owned.inputs().thirdParties();
        OnFile onFile = new OnFile(particularsOnFile.landlords(parties.landlordIds()),
                particularsOnFile.lenders(parties.lenderIds()));
        return new DraftDocumentChecklist(today, inputs.regime().value(), assembled.draft().getCurrentRevision(),
                claims(inputs, owned.inputs().evidence(), onFile),
                Stream.concat(DraftComputationService.NOT_INCLUDED.stream(), NOT_INCLUDED.stream()).toList());
    }

    /** Whether the draft's landlords and lenders have their particulars on file. */
    record OnFile(boolean landlords, boolean lenders) {
    }

    /** The claims and their documents; separate from {@link #checklist} so it is tested on the engine alone. */
    List<DraftDocumentChecklist.Claim> claims(EngineInputs inputs, EvidenceHeld held, OnFile onFile) {
        Reading reading = reading(inputs, held, onFile);
        return taxCalculator.priceClaims(inputs).stream().map(PricedClaim::playbook)
                .map(playbook -> new DraftDocumentChecklist.Claim(playbook.rule(), playbook.plainName(),
                        playbook.guidance().evidence().stream().map(item -> document(playbook, item, reading)).toList()))
                .toList();
    }

    /** What the draft, its profile and the engine show, for reading each record the catalogue names. */
    Reading reading(EngineInputs inputs, EvidenceHeld held, OnFile onFile) {
        Set<String> asked = new TreeSet<>();
        taxCalculator.compute(inputs).workings().notices().forEach(notice -> asked.addAll(notice.relatedRules()));
        return new Reading(new EvidencedFacts(inputs.entered(), held, inputs.taxYear()), onFile, asked,
                certificatesHeld(inputs), taxCalculator.evidenceSentences(inputs.taxYear()));
    }

    private static Document document(ClaimPlaybook playbook, ClaimPlaybook.Evidence item, Reading reading) {
        Optional<String> askFor = playbook.guidance().askWhom().stream()
                .filter(request -> request.whom().equals(item.from())).map(ClaimPlaybook.Request::askFor).findFirst();
        Reading.Read read = reading.of(item.recordedAs(), playbook.rule());
        return new Document(item.name(), item.closedBy(), item.from(), askFor, read.state(), read.outstanding(),
                read.says());
    }

    private static boolean certificatesHeld(EngineInputs inputs) {
        return inputs.entered().houseProperties().stream().map(HouseProperty::loans).flatMap(List::stream)
                .allMatch(HousingLoan::lenderCertificateHeld);
    }

    /**
     * What the draft shows for each record the catalogue names, by its name: the catalogue's type is inside the engine,
     * so a name this does not read is refused rather than shown as held ({@code DraftDocumentChecklistServiceTest}
     * holds every name the catalogue can give to a case here).
     */
    record Reading(EvidencedFacts facts, OnFile onFile, Set<String> asked, boolean certificatesHeld,
            Map<String, String> sentences) {

        record Read(State state, Optional<Outstanding> outstanding, String says) {
        }

        Read of(String record, String rule) {
            return switch (record) {
                case "RENT_RECEIPTS" -> months(facts.monthsNotReceipted());
                case "JOURNEY_EXPENDITURE" -> amount(facts.travelNotEvidenced(), record);
                case "QUALIFYING_PAYMENT" -> amount(facts.paymentsNotEvidenced(), record);
                case "HEALTH_PAYMENT", "DONATION", "DEPENDANT_SUPPORT" ->
                        amount(facts.chapterViiiNotEvidenced(EvidenceKind.valueOf(record)), record);
                case "LANDLORD_PARTICULARS" -> asked.contains(rule)
                        ? onFile(onFile.landlords(), "LANDLORD_ON_FILE", "LANDLORD_NOT_ON_FILE")
                        : new Read(State.NOT_ASKED, Optional.empty(), sentence("LANDLORD_NOT_ASKED"));
                case "LENDER_PARTICULARS" -> onFile(onFile.lenders(), "LENDER_ON_FILE", "LENDER_NOT_ON_FILE");
                case "LENDER_CERTIFICATE" ->
                        onFile(certificatesHeld, "LENDER_CERTIFICATE_HELD", "LENDER_CERTIFICATE_NOT_HELD");
                case "NOT_RECORDED" -> new Read(State.NOT_RECORDED, Optional.empty(), sentence("NOT_RECORDED"));
                default -> throw new IllegalStateException("The claim catalogue names a record this checklist cannot"
                        + " read: " + record);
            };
        }

        private Read months(long notReceipted) {
            if (notReceipted == 0) {
                return new Read(State.HELD, Optional.empty(), sentence("FULLY_EVIDENCED"));
            }
            return new Read(State.NOT_HELD, Optional.of(new Outstanding(Optional.empty(), Optional.of(notReceipted))),
                    sentence("RENT_RECEIPTS").replace("{months}", Long.toString(notReceipted)));
        }

        private Read amount(Money notEvidenced, String record) {
            if (!notEvidenced.isGreaterThan(Money.ZERO)) {
                return new Read(State.HELD, Optional.empty(), sentence("FULLY_EVIDENCED"));
            }
            return new Read(State.NOT_HELD, Optional.of(new Outstanding(Optional.of(notEvidenced), Optional.empty())),
                    sentence(record).replace("{amount}", notEvidenced.inIndianNotation()));
        }

        private Read onFile(boolean held, String heldSentence, String notHeldSentence) {
            return held ? new Read(State.HELD, Optional.empty(), sentence(heldSentence))
                    : new Read(State.NOT_HELD, Optional.empty(), sentence(notHeldSentence));
        }

        private String sentence(String code) {
            String sentence = sentences.get(code);
            if (sentence == null) {
                throw new IllegalStateException("The year's content has no evidence sentence " + code);
            }
            return sentence;
        }
    }
}
