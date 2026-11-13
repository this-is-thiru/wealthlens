package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What one form says of one return (DR 21 I4; W-091, D-653): open to it, closed by named reasons, excluded by an earlier
 * form open to it, or not yet settled because a question it reads is unanswered (C3a). Every form's verdict is kept, not
 * only the chosen one's, because "not ITR-1, for these reasons" is the answer a person needs.
 *
 * @param form the form
 * @param standing which of the four it is
 * @param conditionsFailed each positive condition that does not hold, as the condition describes itself
 * @param barsHeld each bar that holds, with its reason and source
 * @param excludedBy the earlier form, open to the return, that this form excludes
 * @param unanswered the questions a condition or bar reads that the person has not answered
 */
public record FormVerdict(
        ItrFormCode form,
        Standing standing,
        List<String> conditionsFailed,
        List<ItrFormBar> barsHeld,
        Optional<ItrFormCode> excludedBy,
        Set<DeclarationCode> unanswered) {

    /** Where a form stands for a return. */
    public enum Standing { ELIGIBLE, INELIGIBLE, EXCLUDED, UNDETERMINED }

    public FormVerdict {
        if (form == null || standing == null || excludedBy == null) {
            throw new IllegalArgumentException("A verdict names its form, its standing and whether a form excluded it");
        }
        conditionsFailed = List.copyOf(conditionsFailed);
        barsHeld = List.copyOf(barsHeld);
        unanswered = Set.copyOf(unanswered);
    }

    /** The verdict of a form excluded by an earlier form open to the return. */
    static FormVerdict excluded(ItrFormCode form, ItrFormCode by) {
        return new FormVerdict(form, Standing.EXCLUDED, List.of(), List.of(), Optional.of(by), Set.of());
    }

    /**
     * The verdict of a form tested: closed where a condition fails or a bar holds, whatever is unanswered; otherwise
     * undetermined while a question is unanswered, and open when none is.
     */
    static FormVerdict tested(ItrFormCode form, List<String> conditionsFailed, List<ItrFormBar> barsHeld,
            Set<DeclarationCode> unanswered) {
        Standing standing;
        if (!conditionsFailed.isEmpty() || !barsHeld.isEmpty()) {
            standing = Standing.INELIGIBLE;
        } else if (!unanswered.isEmpty()) {
            standing = Standing.UNDETERMINED;
        } else {
            standing = Standing.ELIGIBLE;
        }
        return new FormVerdict(form, standing, conditionsFailed, barsHeld, Optional.empty(), unanswered);
    }
}
