package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.AbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.BucketPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsAbsorptionOrder;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsParts;
import com.thiru.wealthlens.taxengine.rules.definition.CashPaymentLimit;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DayWindow;
import com.thiru.wealthlens.taxengine.rules.definition.DearnessAllowanceTest;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.DisallowanceBasis;
import com.thiru.wealthlens.taxengine.rules.definition.ExcludedAssets;
import com.thiru.wealthlens.taxengine.rules.definition.FamilyPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.LossCarryForward;
import com.thiru.wealthlens.taxengine.rules.definition.MovableAssetKind;
import com.thiru.wealthlens.taxengine.rules.definition.OwnResidenceHouses;
import com.thiru.wealthlens.taxengine.rules.definition.OwnResidenceInterestLimit;
import com.thiru.wealthlens.taxengine.rules.definition.ParticularsThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.PolicyIssueWindow;
import com.thiru.wealthlens.taxengine.rules.definition.PolicyPremiumLimits;
import com.thiru.wealthlens.taxengine.rules.definition.PremiumShareBands;
import com.thiru.wealthlens.taxengine.rules.definition.ProvidentFundThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.definition.SanctionedLoanLimit;
import com.thiru.wealthlens.taxengine.rules.definition.ShortTermHoldingPeriod;
import com.thiru.wealthlens.taxengine.rules.definition.SpecialRateShortfallOrder;
import com.thiru.wealthlens.taxengine.rules.definition.SttChargeability;
import com.thiru.wealthlens.taxengine.rules.definition.SurvivingChildrenLimit;
import com.thiru.wealthlens.taxengine.rules.definition.TaxYearSpan;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * A rule's definition (D-091): what it says an amount is — a head's total, a salary base, a share, a threshold, an
 * order. Read from the tagged union keyed on {@code "type"}, each failure naming the path.
 */
public final class DefinitionJson {

    private DefinitionJson() {
    }

    /** A rule's definition (D-091): what it says an amount is. */
    public static RuleDefinition definition(JsonNode node, String path) {
        String tag = PackJson.tag(
                node, path, "HeadTotal", "SalaryBase", "JourneysPerCalendarBlock", "DisabledTransportLimit",
                "CommutedPensionShare", "IndexByYear", "ExcludedAssets", "DayWindow", "PremiumShareBands", "TaxYearSpan", "SanctionedLoanLimit",
                "CashPaymentLimit", "OwnResidenceHouses", "OwnResidenceInterestLimit", "DisallowanceBasis",
                "ParticularsThreshold", "FamilyPensionShare", "ProvidentFundThreshold", "GiftThreshold", "PolicyPremiumLimits",
                "SurvivingChildrenLimit", "AbsorptionOrder", "CapitalGainsAbsorptionOrder",
                "SpecialRateShortfallOrder", "LossCarryForward", "BucketPart", "SttChargeability", "ShortTermHoldingPeriod",
                "CapitalGainsParts");
        return switch (tag) {
            case "HeadTotal" -> {
                HeadCode head = PackJson.enumValue(node, "head", path, HeadCode.class);
                yield PackJson.construct(path, () -> new HeadTotal(head));
            }
            case "SalaryBase" -> PackJson.construct(path, () -> new SalaryBase(
                    PackJson.enumValue(node, "dearnessAllowance", path, DearnessAllowanceTest.class)));
            case "JourneysPerCalendarBlock" -> PackJson.construct(path, () -> new JourneysPerCalendarBlock(
                    PackJson.integer(node, "journeys", path), PackJson.integer(node, "calendarYears", path),
                    PackJson.integer(node, "blocksCommence", path)));
            case "DisabledTransportLimit" -> disabledTransportLimit(node, path);
            case "CommutedPensionShare" -> PackJson.construct(path, () -> new CommutedPensionShare(
                    PackJson.integer(node, "oneOverWhereGratuityReceived", path),
                    PackJson.integer(node, "oneOverOtherwise", path)));
            case "FamilyPensionShare" -> PackJson.construct(path, () -> new FamilyPensionShare(
                    PackJson.integer(node, "oneOver", path), PackJson.money(node, "limit", path)));
            case "ProvidentFundThreshold" -> PackJson.construct(path, () -> new ProvidentFundThreshold(
                    PackJson.money(node, "withoutEmployerContribution", path), PackJson.money(node, "otherwise", path)));
            case "IndexByYear" -> indexByYear(node, path);
            case "DayWindow" -> PackJson.construct(path, () -> new DayWindow(PackJson.integer(node, "days", path)));
            case "DisallowanceBasis" -> PackJson.construct(path, () -> new DisallowanceBasis(
                    new DisallowanceSetId(PackJson.text(node, "set", path))));
            case "ExcludedAssets" -> {
                List<MovableAssetKind> kinds = PackJson.array(node, "kinds", path,
                        e -> constant(e.node().asString(), e.path(), MovableAssetKind.class));
                yield PackJson.construct(path, () -> new ExcludedAssets(Set.copyOf(kinds)));
            }
            default -> chapterEightDefinition(tag, node, path);
        };
    }

    /**
     * The Chapter VIII deductions' definitions, other sources' gift and life-policy limits and the leave travel
     * concession's children limit — apart only to keep each switch within the complexity limit.
     */
    private static RuleDefinition chapterEightDefinition(String tag, JsonNode node, String path) {
        return switch (tag) {
            case "PremiumShareBands" -> PackJson.construct(path, () -> new PremiumShareBands(PackJson.array(
                    node, "bands", path, band -> new PremiumShareBands.Band(new PolicyIssueWindow(
                            PackJson.optionalDate(band.node(), "from", path),
                            PackJson.optionalDate(band.node(), "to", path),
                            PackJson.bool(band.node(), "onlyCoveringDisabilityOrDisease", path)),
                            PackJson.rate(band.node(), "percent", path)))));
            case "GiftThreshold" -> PackJson.construct(path, () -> new GiftThreshold(PackJson.money(node, "exceeds", path),
                    PackJson.rate(node, "considerationSharePercent", path)));
            case "PolicyPremiumLimits" -> PackJson.construct(path, () -> new PolicyPremiumLimits(
                    PackJson.date(node, "unitLinkedFrom", path), PackJson.money(node, "unitLinkedLimit", path),
                    PackJson.date(node, "otherFrom", path), PackJson.money(node, "otherLimit", path),
                    PackJson.optionalDate(node, "ifscIssuedFrom", path)));
            case "SurvivingChildrenLimit" -> PackJson.construct(path, () -> new SurvivingChildrenLimit(
                    PackJson.integer(node, "children", path), PackJson.date(node, "exceptBornBefore", path)));
            case "TaxYearSpan" -> PackJson.construct(path, () -> new TaxYearSpan(
                    PackJson.integer(node, "succeedingYears", path)));
            case "SanctionedLoanLimit" -> PackJson.construct(path, () -> new SanctionedLoanLimit(
                    PackJson.money(node, "limit", path), PackJson.date(node, "sanctionedFrom", path),
                    PackJson.date(node, "sanctionedTo", path), PackJson.optionalMoney(node, "maxAmountSanctioned", path),
                    PackJson.optionalMoney(node, "maxPropertyValue", path), PackJson.optionalMoney(node, "maxStampDutyValue", path),
                    PackJson.bool(node, "requiresNoHouseOwned", path)));
            case "CashPaymentLimit" -> PackJson.construct(path, () -> new CashPaymentLimit(PackJson.money(node, "atMost", path)));
            case "ParticularsThreshold" -> PackJson.construct(path, () -> new ParticularsThreshold(
                    PackJson.money(node, "exceeds", path)));
            case "OwnResidenceHouses" -> PackJson.construct(path, () -> new OwnResidenceHouses(
                    PackJson.integer(node, "houses", path)));
            case "OwnResidenceInterestLimit" -> PackJson.construct(path, () -> new OwnResidenceInterestLimit(
                    PackJson.money(node, "conditionsMet", path), PackJson.money(node, "anyOtherCase", path),
                    PackJson.money(node, "aggregate", path), PackJson.integer(node, "completionYearsAfterBorrowingYear", path),
                    PackJson.optionalDate(node, "borrowedOnOrAfter", path)));
            default -> setOffDefinition(tag, node, path);
        };
    }

    /**
     * The orders in which heads, and the parts of the capital-gains head, absorb a loss (D-217, D-243), how a loss is
     * carried forward (D-263), the special-rate parts the unused basic exemption (D-244), and the part a capital-gains
     * bucket falls in (D-289), whether a side's STT was chargeable (D-298) and the parts themselves (D-309).
     */
    private static RuleDefinition setOffDefinition(String tag, JsonNode node, String path) {
        return switch (tag) {
            case "AbsorptionOrder" -> {
                List<HeadCode> heads = PackJson.array(node, "heads", path,
                        e -> constant(e.node().asString(), e.path(), HeadCode.class));
                yield PackJson.construct(path, () -> new AbsorptionOrder(heads));
            }
            case "CapitalGainsAbsorptionOrder" -> {
                List<CapitalGainsPart> parts = PackJson.array(node, "parts", path,
                        e -> PackJson.construct(e.path(), () -> new CapitalGainsPart(e.node().asString())));
                yield PackJson.construct(path, () -> new CapitalGainsAbsorptionOrder(parts));
            }
            case "LossCarryForward" -> {
                HeadCode absorbedBy = PackJson.enumValue(node, "absorbedBy", path, HeadCode.class);
                yield PackJson.construct(path, () -> new LossCarryForward(absorbedBy,
                        new TaxYearSpan(PackJson.integer(node, "succeedingYears", path)),
                        PackJson.bool(node, "requiresReturnFiledInTime", path)));
            }
            case "SpecialRateShortfallOrder" -> {
                List<CapitalGainsPart> parts = PackJson.array(node, "parts", path,
                        e -> PackJson.construct(e.path(), () -> new CapitalGainsPart(e.node().asString())));
                yield PackJson.construct(path, () -> new SpecialRateShortfallOrder(parts));
            }
            case "BucketPart" -> PackJson.construct(path, () -> new BucketPart(
                    new CapitalGainsPart(PackJson.text(node, "part", path))));
            case "SttChargeability" -> PackJson.construct(path, () -> new SttChargeability(
                    PackJson.text(node, "code", path)));
            case "ShortTermHoldingPeriod" -> PackJson.construct(path, () -> new ShortTermHoldingPeriod(
                    PackJson.integer(node, "months", path)));
            case "CapitalGainsParts" -> {
                List<CapitalGainsParts.Declared> parts = PackJson.array(node, "parts", path,
                        e -> PackJson.construct(e.path(), () -> new CapitalGainsParts.Declared(
                                new CapitalGainsPart(PackJson.text(e.node(), "part", e.path())),
                                PackJson.text(e.node(), "term", e.path()),
                                PackJson.bool(e.node(), "canAbsorbLongTermLoss", e.path()))));
                yield PackJson.construct(path, () -> new CapitalGainsParts(parts));
            }
            default -> throw unreachable(tag, path);
        };
    }

    /** Each entry names its financial year as a label, {@code "2023-24"}, so the table reads as the notification. */
    private static IndexByYear indexByYear(JsonNode node, String path) {
        List<Map.Entry<TaxYearId, Integer>> entries = PackJson.array(node, "years", path,
                e -> Map.entry(financialYear(PackJson.text(e.node(), "financialYear", e.path()), e.path()),
                        PackJson.integer(e.node(), "index", e.path())));
        Map<TaxYearId, Integer> table = new LinkedHashMap<>();
        for (Map.Entry<TaxYearId, Integer> entry : entries) {
            if (table.put(entry.getKey(), entry.getValue()) != null) {
                throw new PackValidationException(path + ".years lists " + entry.getKey().label() + " twice");
            }
        }
        return PackJson.construct(path, () -> new IndexByYear(table));
    }

    private static TaxYearId financialYear(String label, String path) {
        TaxYearId year;
        try {
            year = TaxYearId.of(Integer.parseInt(label.substring(0, Math.min(4, label.length()))));
        } catch (NumberFormatException e) {
            throw new PackValidationException(path + ".financialYear must read like \"2023-24\", but was \"" + label
                    + "\"");
        }
        if (!year.label().equals(label)) {
            throw new PackValidationException(path + ".financialYear must read like \"2023-24\", but was \"" + label
                    + "\"");
        }
        return year;
    }

    private static DisabledTransportLimit disabledTransportLimit(JsonNode node, String path) {
        List<TransportDisability> conditions = PackJson.array(node, "conditions", path,
                e -> constant(e.node().asString(), e.path(), TransportDisability.class));
        Money perMonth = PackJson.money(node, "perMonth", path);
        Optional<Money> perMonthInOtherCities = PackJson.optionalMoney(node, "perMonthInOtherCities", path);
        boolean plusDearnessAllowance = PackJson.bool(node, "plusDearnessAllowanceThereon", path);
        return PackJson.construct(path, () -> new DisabledTransportLimit(
                Set.copyOf(conditions), perMonth, perMonthInOtherCities, plusDearnessAllowance));
    }

    /** An enum constant, named in the failure with the constants there are. */
    private static <E extends Enum<E>> E constant(String raw, String path, Class<E> type) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new PackValidationException(path + " must be one of "
                + java.util.Arrays.toString(type.getEnumConstants()) + ", but was \"" + raw + "\"");
    }

    private static PackValidationException unreachable(String tag, String path) {
        return new PackValidationException(path + " has unhandled type \"" + tag + "\"");
    }
}
