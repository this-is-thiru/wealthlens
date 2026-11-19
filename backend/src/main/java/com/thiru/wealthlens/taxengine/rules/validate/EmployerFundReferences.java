package com.thiru.wealthlens.taxengine.rules.validate;

import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.condition.AllOf;
import com.thiru.wealthlens.taxengine.rules.condition.AnyOf;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFactIs;
import com.thiru.wealthlens.taxengine.rules.condition.Not;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * W-238e — the employer-fund registry agrees with the rules: every rule whose conditions read {@code employer.fund}
 * names a fund with a row, and that row lists the rule; every rule a row lists is in the pack.
 *
 * <p>A row lists more than the conditions show — a node reaches the aggregate limit with every contribution, and its
 * note says so — so the check is one way from the conditions: a rule added for a fund without its row would leave the
 * row telling a user less than the law does, and a row naming a rule the year lacks would tell them more.
 */
final class EmployerFundReferences {

    private static final ItemFactId EMPLOYER_FUND = new ItemFactId("employer.fund");

    private EmployerFundReferences() {
    }

    static void check(RulePack pack, List<String> problems) {
        for (RuleRow row : pack.rules().values()) {
            row.versions().stream().map(RuleVersion::conditions).flatMap(List::stream)
                    .flatMap(EmployerFundReferences::fundsRead).distinct()
                    .forEach(code -> requireListed(pack, row.id(), code, problems));
        }
        for (EmployerFundDefinition fund : pack.funds().values()) {
            fund.feedsInto().stream().filter(rule -> !pack.rules().containsKey(rule)).map(RuleId::value).sorted()
                    .forEach(rule -> problems.add("employer fund " + fund.code() + " feeds " + rule
                            + ", which is not a rule of this pack"));
        }
    }

    private static void requireListed(RulePack pack, RuleId rule, String code, List<String> problems) {
        Optional<EmployerFundDefinition> fund = Optional.ofNullable(pack.funds().get(new EmployerFundCode(code)));
        if (fund.isEmpty()) {
            problems.add("rule " + rule + " reads employer fund " + code + ", which has no row in the fund registry");
        } else if (!fund.get().feedsInto().contains(rule)) {
            problems.add("rule " + rule + " reads employer fund " + code + ", whose row does not list it in feedsInto");
        }
    }

    /** The funds an {@code employer.fund} condition names, through {@code AllOf}, {@code AnyOf} and {@code Not}. */
    private static Stream<String> fundsRead(Condition condition) {
        return switch (condition) {
            case ItemFactIs is when is.fact().equals(EMPLOYER_FUND) -> Stream.of(is.code());
            case AllOf all -> all.all().stream().flatMap(EmployerFundReferences::fundsRead);
            case AnyOf any -> any.any().stream().flatMap(EmployerFundReferences::fundsRead);
            case Not not -> fundsRead(not.condition());
            default -> Stream.empty();
        };
    }
}
