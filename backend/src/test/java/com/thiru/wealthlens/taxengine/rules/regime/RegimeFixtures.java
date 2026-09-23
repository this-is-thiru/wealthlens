package com.thiru.wealthlens.taxengine.rules.regime;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.registry.DraftOrFinal;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationStatus;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Shared regime fixtures for the behaviour tests. */
final class RegimeFixtures {

    private RegimeFixtures() {
    }

    static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025",
                DraftOrFinal.FINAL,
                LocalDate.of(2026, 9, 19),
                VerificationStatus.VERIFIED,
                null);
    }

    static RegimeSpec regimeWithBehaviour(BehaviourId behaviour) {
        return new RegimeSpec(
                new RegimeCode("S202_DEFAULT"),
                "Default regime (s.202(1))",
                Provision.notYetVerified("Income-tax Act, 1961"),
                Provision.present("Income-tax Act, 2025", "s.202(1)"),
                Set.of(PersonType.INDIVIDUAL),
                new RuleId("RATE-SLAB-NEW-2026"),
                Set.of(),
                Set.of(),
                new ElectionProtocol(
                        ElectionKind.DEFAULT_NO_ACTION,
                        ElectionVehicle.NONE,
                        Optional.empty(),
                        new DeadlineSpec.NoDeadline(),
                        BindingSpec.free(),
                        provenance()),
                new RegimeLifecycle(
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        new ContinuationPolicy.ContinueIndefinitely(),
                        new InvalidationPolicy.NoInvalidation(),
                        provenance()),
                true,
                new PipelineSpec(List.of(new NodeId("tax.bybasis"))),
                Optional.ofNullable(behaviour),
                provenance());
    }
}
