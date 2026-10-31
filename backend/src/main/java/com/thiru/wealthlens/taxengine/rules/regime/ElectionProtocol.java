package com.thiru.wealthlens.taxengine.rules.regime;

import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.List;
import java.util.Optional;

/**
 * How a regime is chosen: by what act, recorded where, by when, binding what.
 *
 * <p>Replaces a three-valued {@code lockIn} enum with the four questions that enum was standing in
 * for. The reason is {@code REG-OPTOUT-FORM-001}: for TY 2026-27 rule 136 puts the option
 * <em>in the return</em> with <b>no separate form</b>, while for TY 2025-26 Income-tax Rules, 1962
 * r.21AGA(1) puts it in Form No. 10-IEA for a person with business income and in the return for one
 * without — two years of the same regime differing in vehicle, form and deadline, and one year
 * differing by taxpayer, which an enum cannot express and a product that gets wrong sends the user
 * looking for a form that does not exist (or past one that does).
 *
 * @param kind whether the regime is the default, opted into, or opted out of
 * @param vehicle where the choice is recorded
 * @param form the prescribed form — present if and only if {@code vehicle} is
 *     {@link ElectionVehicle#PRESCRIBED_FORM}
 * @param vehicleWhen routes for classes of taxpayer whose vehicle differs, first match wins; empty
 *     means everyone uses {@code vehicle} and {@code form}
 * @param deadline when the choice must be made by
 * @param binding what the choice commits the taxpayer to
 * @param provenance where this protocol was read from, including whether it is verified
 */
public record ElectionProtocol(
        ElectionKind kind,
        ElectionVehicle vehicle,
        Optional<FormRef> form,
        List<VehicleRoute> vehicleWhen,
        DeadlineSpec deadline,
        BindingSpec binding,
        Provenance provenance) {

    public ElectionProtocol {
        if (kind == null || vehicle == null) {
            throw new IllegalArgumentException("An election protocol needs both a kind and a vehicle");
        }
        if (deadline == null) {
            throw new IllegalArgumentException(
                    "An election protocol must state a deadline; use DeadlineSpec.NoDeadline where"
                            + " nothing has to be done");
        }
        if (binding == null) {
            throw new IllegalArgumentException(
                    "An election protocol must state its binding; use BindingSpec.free() for a"
                            + " choice that commits the taxpayer to nothing");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("An election protocol must say where it was read from");
        }
        form = form == null ? Optional.empty() : form;
        // The form rule lives on VehicleRoute; building the protocol's own route applies it here too.
        new VehicleRoute(List.of(), vehicle, form);
        vehicleWhen = vehicleWhen == null ? List.of() : List.copyOf(vehicleWhen);
        if (vehicleWhen.stream().anyMatch(route -> route.appliesWhen().isEmpty())) {
            throw new IllegalArgumentException(
                    "A vehicle route must say whom it is for; one that applies to everyone hides the"
                            + " protocol's own vehicle");
        }
    }

    /** Where this taxpayer records the choice: the first route that is theirs, else the protocol's own. */
    public VehicleRoute vehicleFor(ConditionContext ctx) {
        return vehicleWhen.stream()
                .filter(route -> route.appliesTo(ctx))
                .findFirst()
                .orElseGet(() -> new VehicleRoute(List.of(), vehicle, form));
    }
}
