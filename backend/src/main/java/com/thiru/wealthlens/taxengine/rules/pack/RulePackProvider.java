package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Map;
import java.util.Set;

/**
 * Where rule packs come from.
 *
 * <p>An SPI so that a future source — a signed remote pack, say — is a new class rather than a
 * change here. {@link com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider} compiles a year from the
 * classpath; {@link com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks} holds what the application
 * compiled at startup (D-210). There is deliberately no
 * database-backed provider and no administrative write path: a pack is an immutable, reviewed,
 * checksummed artefact, and a pack that can be edited in production is one nobody can reproduce a
 * computation from ({@code D-IMMUTABLE}).
 *
 * <p><b>Validation is not here.</b> {@code 07-architecture.md} X2.4 put a {@code validate} method on
 * this interface; it lives in the pack validator instead. A provider's job is to source packs, and
 * every provider validating independently is how two providers come to disagree about what a valid
 * pack is.
 */
public interface RulePackProvider {

    /** Which tax years this source can supply. */
    Set<TaxYearId> availableYears();

    /**
     * Each rule's checksum in the year's verified lock: what tells a stored computation which of the rules it read have changed
     * since (W-138, {@code 13-edge-cases.md} R-8).
     *
     * @throws com.thiru.wealthlens.taxengine.rules.validate.PackValidationException if this source has no such year
     */
    Map<RuleId, String> ruleChecksums(TaxYearId year);

    /**
     * The pack for a year.
     *
     * <p><b>Throws rather than returning a partial pack.</b> Half a pack computes half a tax bill
     * and says nothing about the other half.
     *
     * @throws com.thiru.wealthlens.taxengine.rules.validate.PackValidationException if the pack is absent,
     *     malformed, or does not match the checksums its own manifest declares
     */
    RulePack load(TaxYearId year);
}
