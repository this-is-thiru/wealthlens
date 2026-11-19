package com.thiru.wealthlens.taxengine.rules.regime;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import java.util.List;
import java.util.Optional;

/**
 * Where one class of taxpayer records a choice: the vehicle, and the form if one is prescribed.
 *
 * <p>Exists because one year's option can need two vehicles. Income-tax Rules, 1962 r.21AGA(1)
 * (G.S.R. 452(E), 21-6-2023): the option under s.115BAC(6) is made <em>"(a) in Form No. 10-IEA on or
 * before the due date specified under sub-section (1) of section 139 … in the case of a person having
 * income from business or profession; (b) in the return of income … in the case of a person not having
 * income from business or profession"</em>. The same idiom as {@link BindingSpec#bindingAppliesWhen()}:
 * who the route is for is a list of conditions, so the split lives in data.
 *
 * @param appliesWhen who takes this route; empty means everyone
 * @param vehicle where the choice is recorded
 * @param form the prescribed form — present if and only if {@code vehicle} is
 *     {@link ElectionVehicle#PRESCRIBED_FORM}
 */
public record VehicleRoute(List<Condition> appliesWhen, ElectionVehicle vehicle, Optional<FormRef> form) {

    public VehicleRoute {
        if (vehicle == null) {
            throw new IllegalArgumentException("A vehicle route must name its vehicle");
        }
        appliesWhen = appliesWhen == null ? List.of() : List.copyOf(appliesWhen);
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

    /** Whether this route is the taxpayer's: every condition holds. */
    public boolean appliesTo(ConditionContext ctx) {
        return appliesWhen.stream().allMatch(condition -> condition.holds(ctx));
    }
}
