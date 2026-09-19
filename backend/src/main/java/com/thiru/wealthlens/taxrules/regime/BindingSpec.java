package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.condition.Condition;
import java.util.List;

/**
 * What a choice commits the taxpayer to, and to whom that commitment applies.
 *
 * <h2>{@code bindingAppliesWhen} is the whole idea</h2>
 *
 * <p>It is what lets <b>one</b> regime serve both s.202(4)(a) and s.202(4)(b). The same regime locks
 * in a person <em>with</em> business income — bound for subsequent years, one withdrawal, then
 * barred — and leaves a person <em>without</em> it a free choice every year. That is what the
 * section says. The previous design modelled lock-in as a three-valued enum on the regime, which
 * forced this into a footnote and made the two cases look like two regimes.
 *
 * <p>An empty list means the binding applies to everyone.
 *
 * @param bindsSubsequentYears whether the choice carries into later years — s.202(4)(a)(ii), s.204(2)(b)
 * @param maxWithdrawals {@code 0} irrevocable, {@code -1} unlimited, otherwise a count.
 *     s.202(4)(a)(iii) is {@code 1}
 * @param afterWithdrawal what the taxpayer may do once the withdrawals are used up
 * @param firstReturnOnly the choice may be made only with the first return ever filed — s.204(2)(a)
 * @param bindingAppliesWhen who this binding applies to; empty means everyone
 */
public record BindingSpec(
        boolean bindsSubsequentYears,
        int maxWithdrawals,
        AfterWithdrawal afterWithdrawal,
        boolean firstReturnOnly,
        List<Condition> bindingAppliesWhen) {

    public BindingSpec {
        if (maxWithdrawals < -1) {
            throw new IllegalArgumentException(
                    "maxWithdrawals must be -1 (unlimited), 0 (irrevocable) or a count, but was "
                            + maxWithdrawals);
        }
        if (afterWithdrawal == null) {
            throw new IllegalArgumentException(
                    "A binding must say what happens after withdrawal, even where none is permitted");
        }
        bindingAppliesWhen =
                bindingAppliesWhen == null ? List.of() : List.copyOf(bindingAppliesWhen);
    }

    /** A choice that binds nobody to anything — a free annual election. */
    public static BindingSpec free() {
        return new BindingSpec(false, -1, new AfterWithdrawal.MayReElect(), false, List.of());
    }

    /** The choice cannot be withdrawn at all. */
    public boolean isIrrevocable() {
        return maxWithdrawals == 0;
    }

    /** The choice may be withdrawn and remade without limit. */
    public boolean isUnlimitedWithdrawals() {
        return maxWithdrawals == -1;
    }

    /**
     * Whether this binding applies to a taxpayer at all.
     *
     * <p>Where it does not, the regime is a free annual choice for them regardless of what the rest
     * of this record says — which is the s.202(4)(b) case for a salaried taxpayer.
     */
    public boolean appliesTo(com.thiru.wealthlens.taxrules.condition.ConditionContext ctx) {
        return bindingAppliesWhen.stream().allMatch(condition -> condition.holds(ctx));
    }
}
