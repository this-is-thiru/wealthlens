package com.thiru.wealthlens.taxengine.rules.timeline;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackProvider;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule packs the application serves: each shipped year compiled from the timeline and verified against its
 * lock once, before anything asks for it (D-210; {@code DR 07} X2).
 *
 * <p>Compiling up front is the point. A timeline that no longer matches a lock then fails where it is made — at
 * startup, naming each rule that moved — instead of on the first request for that year, and no request ever pays
 * for a compile. Each year is assembled with the instruments its own lock names, so one year's Act cannot be
 * cited for another's law.
 */
public final class ShippedRulePacks implements RulePackProvider {

    private final Map<TaxYearId, RulePack> packs;

    private ShippedRulePacks(Map<TaxYearId, RulePack> packs) {
        this.packs = Map.copyOf(packs);
    }

    /**
     * Compiles and verifies every year in {@code years}, or refuses them all.
     *
     * @param root the classpath directory holding {@code timeline/} and each year's lock
     * @param years the years shipped; declared rather than scanned, as {@link ClasspathRulePackProvider} explains
     */
    public static ShippedRulePacks compile(String root, Set<TaxYearId> years) {
        if (years.isEmpty()) {
            throw new IllegalArgumentException("An application shipping no tax years cannot compute anything");
        }
        Map<TaxYearId, RulePack> packs = new HashMap<>();
        for (TaxYearId year : years) {
            JsonPackAssembler assembler = new JsonPackAssembler(ClasspathRulePackProvider.lock(root, year).instruments());
            packs.put(year, new ClasspathRulePackProvider(root, Set.of(year), assembler).load(year));
        }
        return new ShippedRulePacks(packs);
    }

    @Override
    public Set<TaxYearId> availableYears() {
        return packs.keySet();
    }

    @Override
    public RulePack load(TaxYearId year) {
        if (year == null || !packs.containsKey(year)) {
            throw new PackValidationException("No pack for " + year + "; the application ships "
                    + new TreeSet<>(packs.keySet().stream().map(TaxYearId::label).toList()));
        }
        return packs.get(year);
    }
}
