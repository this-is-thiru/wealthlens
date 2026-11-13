package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.filing.FormVerdict;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormBar;
import com.thiru.wealthlens.taxengine.rules.filing.ItrFormSpec;
import com.thiru.wealthlens.taxengine.rules.filing.ItrSelection;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.id.ItrFormCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.ReturnKind;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The form a return is furnished in, and why every other form is not, as a reader outside the engine needs it
 * (DR 21 I4; W-091, D-653). Every verdict is returned, not only the winner's.
 *
 * @param taxYear the year, as {@code 2026-27}
 * @param regime the regime the return was computed under, since total income and so bar (k) depend on it
 * @param returnKind the provision the return is furnished under
 * @param form the chosen form; empty where none is open (C4a) or an earlier form waits on an answer (C3a)
 * @param furnishing the manners of furnishing the chosen form open to the person
 * @param verdicts each candidate form's verdict, in cascade order
 * @param message why no form is chosen; empty when one is
 */
public record ItrFormChoice(String taxYear, String regime, String returnKind, Optional<String> form,
        List<String> furnishing, List<Verdict> verdicts, Optional<String> message) {

    /**
     * One form's verdict.
     *
     * @param form the form's code
     * @param displayName the form's name, as {@code SAHAJ (ITR-1)}
     * @param standing {@code ELIGIBLE}, {@code INELIGIBLE}, {@code EXCLUDED} or {@code UNDETERMINED}
     * @param reasons each failed condition, then each bar that holds, as the law words it
     * @param excludedBy the earlier form open to the return that this form excludes
     * @param unanswered the questions the form reads that the person has not answered
     */
    public record Verdict(String form, String displayName, String standing, List<String> reasons,
            Optional<String> excludedBy, List<String> unanswered) {
    }

    static ItrFormChoice of(TaxYearId year, RegimeCode regime, ReturnKind kind, ItrSelection selection,
            Map<ItrFormCode, ItrFormSpec> forms) {
        List<Verdict> verdicts = selection.verdicts().stream().map(verdict -> verdictOf(verdict, forms)).toList();
        return new ItrFormChoice(year.label(), regime.value(), kind.value(), selection.chosen().map(ItrFormCode::value),
                selection.furnishing().stream().map(FurnishingManner::value).toList(), verdicts,
                selection.chosen().isPresent() ? Optional.empty() : Optional.of(whyNone(year, selection)));
    }

    private static Verdict verdictOf(FormVerdict verdict, Map<ItrFormCode, ItrFormSpec> forms) {
        List<String> reasons = Stream.concat(verdict.conditionsFailed().stream(),
                verdict.barsHeld().stream().map(ItrFormBar::reason)).toList();
        return new Verdict(verdict.form().value(), forms.get(verdict.form()).displayName(), verdict.standing().name(),
                reasons, verdict.excludedBy().map(ItrFormCode::value),
                verdict.unanswered().stream().map(DeclarationCode::value).sorted().toList());
    }

    private static String whyNone(TaxYearId year, ItrSelection selection) {
        return selection.verdicts().stream()
                .filter(verdict -> verdict.standing() == FormVerdict.Standing.UNDETERMINED)
                .findFirst()
                .map(verdict -> "Answer " + String.join(", ",
                        verdict.unanswered().stream().map(DeclarationCode::value).sorted().toList())
                        + " to settle whether " + verdict.form().value() + " serves this return")
                .orElse("No form shipped for " + year.label() + " serves this return; each form's reasons are listed");
    }
}
