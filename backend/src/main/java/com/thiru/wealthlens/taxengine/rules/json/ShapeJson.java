package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.cap.LeastOf;
import com.thiru.wealthlens.taxengine.rules.cap.PerUnitPerPeriod;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
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
 * The sealed families a rule version is made of: when it applies, where, and what it limits.
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
                "PerUnitPerPeriod");
        return PackJson.construct(path, () ->
        switch (tag) {
                    case "Fixed" -> new Fixed(money(node, "amount", path));
                    case "PercentOfBase" -> new PercentOfBase(
                            rate(node, "percent", path), new BaseId(PackJson.text(node, "base", path)));
                    case "SlabLadder" -> slabLadder(node, path);
                    case "LeastOf" -> leastOf(node, path);
                    case "AggregateWithCeiling" -> aggregate(node, path);
                    case "PerUnitPerPeriod" -> perUnitPerPeriod(node, path);
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
