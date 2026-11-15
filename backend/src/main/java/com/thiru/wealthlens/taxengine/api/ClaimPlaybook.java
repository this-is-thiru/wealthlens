package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.catalogue.ClaimCatalogueEntry;
import com.thiru.wealthlens.taxengine.rules.catalogue.ClaimStep;
import com.thiru.wealthlens.taxengine.rules.catalogue.CompiledClaim;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * How one claim is made, as the year's claim catalogue states it ({@code DR 22}; W-118 C2a): what it is, on what
 * basis, what its absence from Form 16 means, and the steps — every figure already the year's, in rupees. The
 * catalogue's closed sets are given by name, as {@link ComputedTax} gives codes, so nothing outside the engine depends on
 * its types.
 *
 * @param rule the rule the claim is catalogued under
 * @param covers the other rules it speaks for
 * @param plainName its section's plain name
 * @param summary its section's summary
 * @param basis how it reaches the tax, and where the law says so
 * @param guidance what the person does
 */
public record ClaimPlaybook(String rule, List<String> covers, String plainName, String summary, Basis basis,
        Guidance guidance) {

    public ClaimPlaybook {
        covers = List.copyOf(covers);
    }

    /**
     * @param channel how the claim reaches the computed tax
     * @param chapterViiiPart the Part of Chapter VIII its section sits in, where it is a deduction under that Chapter
     * @param ifMissingFromForm16 what the claim's absence from the employer's certificate means
     * @param provision1961 where the Income-tax Act, 1961 puts it on that channel
     * @param provision2025 where the Income-tax Act, 2025 puts it on that channel
     */
    public record Basis(String channel, Optional<String> chapterViiiPart, String ifMissingFromForm16,
            Citation provision1961, Citation provision2025) {
    }

    /** A provision as the catalogue states it: the reference is empty where its presence is not yet verified. */
    public record Citation(String instrument, Optional<String> reference, String presence) {
    }

    public record Guidance(Optional<String> appliesTo, List<Step> steps, List<Request> askWhom, List<Evidence> evidence,
            List<String> mistakes) {

        public Guidance {
            steps = List.copyOf(steps);
            askWhom = List.copyOf(askWhom);
            evidence = List.copyOf(evidence);
            mistakes = List.copyOf(mistakes);
        }
    }

    /**
     * @param by the deadline's name — the tax year's end, the return's due date, the employer's cut-off, or none
     */
    public record Step(int order, String instruction, String actor, String by, Optional<String> produces,
            boolean warnsOfLateReturnBar) {
    }

    public record Request(String whom, String askFor) {
    }

    public record Evidence(String name, String closedBy) {
    }

    /** The compiled claim, each {@code {{RULE-ID.field}}} replaced by the year's figure in rupees. */
    static ClaimPlaybook of(CompiledClaim claim) {
        UnaryOperator<String> filled = text -> filled(text, claim.figures());
        ClaimCatalogueEntry entry = claim.entry();
        return new ClaimPlaybook(entry.rule().value(), entry.covers().stream().map(rule -> rule.value()).toList(),
                claim.plainName(), claim.summary(),
                new Basis(entry.channel().name(), entry.chapterViiiPart().map(Enum::name),
                        entry.ifMissingFromForm16().name(), citation(entry.basis().provision1961()),
                        citation(entry.basis().provision2025())),
                new Guidance(entry.guidance().appliesTo().map(filled),
                        entry.guidance().steps().stream().map(step -> step(step, filled)).toList(),
                        entry.guidance().askWhom().stream()
                                .map(request -> new Request(request.whom().name(), filled.apply(request.askFor())))
                                .toList(),
                        entry.guidance().evidence().stream()
                                .map(item -> new Evidence(filled.apply(item.name()), filled.apply(item.closedBy())))
                                .toList(),
                        entry.guidance().mistakes().stream().map(filled).toList()));
    }

    private static Step step(ClaimStep step, UnaryOperator<String> filled) {
        return new Step(step.order(), filled.apply(step.instruction()), step.actor().name(), step.by().name(),
                step.produces().map(filled), step.warnsOfLateReturnBar());
    }

    private static Citation citation(Provision provision) {
        return new Citation(provision.instrumentCitation(), Optional.ofNullable(provision.reference()),
                provision.presence().name());
    }

    private static String filled(String text, Map<String, Money> figures) {
        String result = text;
        for (Map.Entry<String, Money> figure : figures.entrySet()) {
            result = result.replace("{{" + figure.getKey() + "}}", figure.getValue().inIndianNotation());
        }
        return result;
    }
}
