package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.registry.Provenance;
import java.util.Optional;

/**
 * How a regime is chosen: by what act, recorded where, by when, binding what.
 *
 * <p>Replaces a three-valued {@code lockIn} enum with the four questions that enum was standing in
 * for. The reason is {@code REG-OPTOUT-FORM-001}: for TY 2026-27 rule 136 puts the option
 * <em>in the return</em> with <b>no separate form</b>, while TY 2025-26 is believed to use Form
 * 10-IEA — two years of the same regime differing in vehicle, form and deadline, which an enum
 * cannot express and a product that gets wrong sends the user looking for a form that does not
 * exist.
 *
 * @param kind whether the regime is the default, opted into, or opted out of
 * @param vehicle where the choice is recorded
 * @param form the prescribed form — present if and only if {@code vehicle} is
 *     {@link ElectionVehicle#PRESCRIBED_FORM}
 * @param deadline when the choice must be made by
 * @param binding what the choice commits the taxpayer to
 * @param provenance where this protocol was read from, including whether it is verified
 */
public record ElectionProtocol(
        ElectionKind kind,
        ElectionVehicle vehicle,
        Optional<FormRef> form,
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

        // Rule 136 in one line: the option rides on the return, so there is no form to name.
        boolean prescribesForm = vehicle == ElectionVehicle.PRESCRIBED_FORM;
        if (prescribesForm && form.isEmpty()) {
            throw new IllegalArgumentException(
                    "vehicle is PRESCRIBED_FORM but no form is named; the taxpayer is told to file"
                            + " something and not what");
        }
        if (!prescribesForm && form.isPresent()) {
            throw new IllegalArgumentException(
                    "vehicle is " + vehicle + " but form " + form.get().number() + " is named;"
                            + " rule 136 puts the option in the return, so naming a form sends the"
                            + " taxpayer looking for one that does not exist");
        }
    }
}
