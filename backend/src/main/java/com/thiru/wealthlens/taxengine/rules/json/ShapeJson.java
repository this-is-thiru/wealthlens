package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.AmountInExcess;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.cap.PerUnitPerPeriod;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBaseByLocation;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.DearnessAllowanceTest;
import com.thiru.wealthlens.taxengine.rules.definition.DisabledTransportLimit;
import com.thiru.wealthlens.taxengine.rules.definition.HeadTotal;
import com.thiru.wealthlens.taxengine.rules.definition.JourneysPerCalendarBlock;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinition;
import com.thiru.wealthlens.taxengine.rules.definition.SalaryBase;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.relief.ExcessOverThreshold;
import com.thiru.wealthlens.taxengine.rules.relief.FlatWithCeiling;
import com.thiru.wealthlens.taxengine.rules.relief.NoRelief;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefShape;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.relief.ThresholdLadderCeiling;
import com.thiru.wealthlens.taxengine.rules.rounding.RuleRounding;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * The sealed families a rule version is made of: when it applies, where, what it limits, what relief
 * it grants, and what it rounds to.
 *
 * <p>Each family is a tagged union keyed on {@code "type"}, and an unrecognised tag fails with the
 * permitted tags listed — because the reader of that message is a pack author choosing between
 * {@code "SlabLadder"} and {@code "SlabLadder "} with a trailing space, not a Java developer.
 */
public final class ShapeJson {

    private ShapeJson() {
    }

    // ---------------------------------------------------------------- Validity

    /** When a rule version was in force. */
    public static Validity validity(JsonNode node, String path, Provenance provenance) {
        String tag = PackJson.tag(node, path, "Between", "Retrospective", "PendingNotification");
        return PackJson.construct(path, () ->
        switch (tag) {
                    case "Between" -> new Validity.Between(
                            PackJson.date(node, "from", path), PackJson.optionalDate(node, "to", path));
                    case "Retrospective" -> new Validity.Retrospective(
                            PackJson.date(node, "deemedEffectiveFrom", path),
                            PackJson.optionalDate(node, "to", path),
                            PackJson.date(node, "actuallyEnactedOn", path));
                    case "PendingNotification" -> new Validity.PendingNotification(
                            new RuleId(PackJson.text(node, "interimRule", path)),
                            PackJson.text(node, "interimDescription", path),
                            provenance);
                    default -> throw unreachable(tag, path);
                });
    }

    // ------------------------------------------------------- RegimeApplicability

    /** Which regimes a rule version applies in. */
    public static RegimeApplicability regimes(JsonNode node, String path) {
        String tag = PackJson.tag(node, path, "AllRegimes", "OnlyIn", "ExceptIn");
        return PackJson.construct(path, () ->
        switch (tag) {
                    case "AllRegimes" -> new RegimeApplicability.AllRegimes();
                    case "OnlyIn" -> new RegimeApplicability.OnlyIn(regimeCodes(node, path));
                    case "ExceptIn" -> new RegimeApplicability.ExceptIn(regimeCodes(node, path));
                    default -> throw unreachable(tag, path);
                });
    }

    private static Set<RegimeCode> regimeCodes(JsonNode node, String path) {
        return PackJson.array(node, "regimes", path, e -> new RegimeCode(e.node().asString()))
                .stream()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ---------------------------------------------------------------- CapShape

    /** A statutory limit. All six shapes. */
    public static CapShape cap(JsonNode node, String path) {
        String tag = PackJson.tag(
                node,
                path,
                "Fixed",
                "PercentOfBase",
                "SlabLadder",
                "LeastOf",
                "AggregateWithCeiling",
                "PerUnitPerPeriod",
                "AmountInExcess",
                "PercentOfBaseByLocation");
        return PackJson.construct(path, () ->
        switch (tag) {
                    case "Fixed" -> new Fixed(money(node, "amount", path));
                    case "PercentOfBase" -> new PercentOfBase(
                            rate(node, "percent", path), new BaseId(PackJson.text(node, "base", path)));
                    case "SlabLadder" -> slabLadder(node, path);
                    case "LeastOf" -> leastOf(node, path);
                    case "AggregateWithCeiling" -> aggregate(node, path);
                    case "PerUnitPerPeriod" -> perUnitPerPeriod(node, path);
                    case "AmountInExcess" -> new AmountInExcess(new BaseId(PackJson.text(node, "amount", path)),
                            cap(PackJson.object(node, "threshold", path), path + ".threshold"));
                    case "PercentOfBaseByLocation" -> percentByLocation(node, path);
                    default -> throw unreachable(tag, path);
                });
    }

    private static SlabLadder slabLadder(JsonNode node, String path) {
        List<SlabLadder.Band> bands = PackJson.array(node, "bands", path, element -> {
            JsonNode band = element.node();
            return new SlabLadder.Band(
                    money(band, "lowerExclusive", element.path()),
                    optionalMoney(band, "upperInclusive", element.path()),
                    rate(band, "rate", element.path()));
        });
        SlabLadder ladder =
                new SlabLadder(bands, new BaseId(PackJson.text(node, "base", path)));
        ladder.validate();
        return ladder;
    }

    private static LeastOf leastOf(JsonNode node, String path) {
        return new LeastOf(PackJson.array(node, "limbs", path, element -> new LeastOf.Limb(
                PackJson.text(element.node(), "name", element.path()),
                cap(PackJson.object(element.node(), "shape", element.path()), element.path() + ".shape"))));
    }

    private static PercentOfBaseByLocation percentByLocation(JsonNode node, String path) {
        List<PercentOfBaseByLocation.Band> bands = PackJson.array(node, "bands", path, element ->
                new PercentOfBaseByLocation.Band(
                        new java.util.LinkedHashSet<>(PackJson.array(element.node(), "locations", element.path(),
                                e -> e.node().asString())),
                        rate(element.node(), "percent", element.path())));
        return new PercentOfBaseByLocation(new BaseId(PackJson.text(node, "base", path)),
                new BaseId(PackJson.text(node, "location", path)), bands, rate(node, "otherwise", path));
    }

    private static AggregateWithCeiling aggregate(JsonNode node, String path) {
        List<ComponentCode> contributors = PackJson.array(
                node, "contributors", path, e -> new ComponentCode(e.node().asString()));

        Map<ComponentCode, CapShape> subCaps = new LinkedHashMap<>();
        PackJson.optionalObject(node, "subCaps", path).ifPresent(caps -> {
            for (String code : caps.propertyNames()) {
                subCaps.put(
                        new ComponentCode(code),
                        cap(caps.get(code), path + ".subCaps." + code));
            }
        });
        return new AggregateWithCeiling(contributors, money(node, "ceiling", path), subCaps);
    }

    private static PerUnitPerPeriod perUnitPerPeriod(JsonNode node, String path) {
        Optional<String> unit = PackJson.optionalText(node, "unit", path);
        return new PerUnitPerPeriod(
                money(node, "amount", path),
                unit.map(BaseId::new),
                unit.isPresent() ? PackJson.integer(node, "maxUnits", path) : 1,
                new BaseId(PackJson.text(node, "period", path)));
    }

    // ---------------------------------------------------------------- ReliefShape

    /**
     * A relief, with its limit to slab-rate tax. The limit has no default: absent is refused, so a
     * rebate's limit is always stated in the pack (D-075).
     */
    public static RuleRelief relief(JsonNode node, String path) {
        String tag = PackJson.tag(
                node, path, "FlatWithCeiling", "ExcessOverThreshold", "ThresholdLadderCeiling", "NoRelief");
        boolean limitedToSlabRateTax = PackJson.bool(node, "limitedToSlabRateTax", path);
        return PackJson.construct(path, () -> {
            ReliefShape shape = switch (tag) {
                case "FlatWithCeiling" -> new FlatWithCeiling(
                        money(node, "maximum", path), money(node, "incomeCeiling", path));
                case "ExcessOverThreshold" -> new ExcessOverThreshold(money(node, "threshold", path));
                case "ThresholdLadderCeiling" -> new ThresholdLadderCeiling(PackJson.array(
                        node, "thresholds", path, e -> moneyElement(e.node(), e.path())));
                case "NoRelief" -> new NoRelief();
                default -> throw unreachable(tag, path);
            };
            return new RuleRelief(shape, limitedToSlabRateTax);
        });
    }

    // ---------------------------------------------------------------- RuleRounding

    /**
     * The step an amount is rounded to. Untagged: rounding is one kind, not a family, so a
     * {@code type} would be a choice with a single option (D-078).
     */
    /** A rule's definition (D-091): what it says an amount is. */
    public static RuleDefinition definition(JsonNode node, String path) {
        String tag = PackJson.tag(
                node, path, "HeadTotal", "SalaryBase", "JourneysPerCalendarBlock", "DisabledTransportLimit",
                "CommutedPensionShare");
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
            default -> throw unreachable(tag, path);
        };
    }

    private static DisabledTransportLimit disabledTransportLimit(JsonNode node, String path) {
        List<TransportDisability> conditions =
                PackJson.array(node, "conditions", path, e -> transportDisability(e.node().asString(), e.path()));
        Money perMonth = money(node, "perMonth", path);
        Optional<Money> perMonthInOtherCities = optionalMoney(node, "perMonthInOtherCities", path);
        boolean plusDearnessAllowance = PackJson.bool(node, "plusDearnessAllowanceThereon", path);
        return PackJson.construct(path, () -> new DisabledTransportLimit(
                Set.copyOf(conditions), perMonth, perMonthInOtherCities, plusDearnessAllowance));
    }

    private static TransportDisability transportDisability(String raw, String path) {
        for (TransportDisability candidate : TransportDisability.values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new PackValidationException(path + " must be one of "
                + java.util.Arrays.toString(TransportDisability.values()) + ", but was \"" + raw + "\"");
    }

    public static RuleRounding rounding(JsonNode node, String path) {
        Money toNearest = money(node, "toNearest", path);
        return PackJson.construct(path, () -> new RuleRounding(toNearest));
    }

    // ---------------------------------------------------------------- money

    private static Money money(JsonNode parent, String field, String path) {
        String raw = PackJson.text(parent, field, path);
        try {
            return Money.of(raw);
        } catch (NumberFormatException e) {
            throw new PackValidationException(
                    path + "." + field + " must be a rupee amount as a string, but was \"" + raw + "\"");
        }
    }

    private static Money moneyElement(JsonNode element, String path) {
        String raw = element.asString();
        try {
            return Money.of(raw);
        } catch (NumberFormatException e) {
            throw new PackValidationException(
                    path + " must be a rupee amount as a string, but was \"" + raw + "\"");
        }
    }

    private static Optional<Money> optionalMoney(JsonNode parent, String field, String path) {
        return PackJson.optionalText(parent, field, path).map(raw -> {
            try {
                return Money.of(raw);
            } catch (NumberFormatException e) {
                throw new PackValidationException(
                        path + "." + field + " must be a rupee amount as a string, but was \"" + raw + "\"");
            }
        });
    }

    private static Rate rate(JsonNode parent, String field, String path) {
        String raw = PackJson.text(parent, field, path);
        try {
            return Rate.percent(raw);
        } catch (NumberFormatException e) {
            throw new PackValidationException(
                    path + "." + field + " must be a percentage as the statute states it, e.g. \"30\","
                            + " but was \"" + raw + "\"");
        }
    }

    private static PackValidationException unreachable(String tag, String path) {
        return new PackValidationException(path + " has unhandled type \"" + tag + "\"");
    }
}
