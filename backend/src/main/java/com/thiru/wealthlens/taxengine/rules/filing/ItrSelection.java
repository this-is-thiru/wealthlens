package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import java.util.List;
import java.util.Optional;

/**
 * The form a return is furnished in, every form's verdict, and the manners open to the person (DR 21 I4; W-091, D-653).
 *
 * @param verdicts each candidate form's verdict, in cascade order
 * @param chosen the first form open to the return, where no earlier form is undetermined; empty when none is open, or an
 *     earlier form waits on an answer
 * @param furnishing the manners of furnishing the chosen form open to the person; empty when none is chosen
 */
public record ItrSelection(List<FormVerdict> verdicts, Optional<ItrFormCode> chosen, List<FurnishingManner> furnishing) {

    public ItrSelection {
        if (chosen == null) {
            throw new IllegalArgumentException("An ItrSelection says whether a form was chosen; empty for none");
        }
        verdicts = List.copyOf(verdicts);
        furnishing = List.copyOf(furnishing);
    }
}
