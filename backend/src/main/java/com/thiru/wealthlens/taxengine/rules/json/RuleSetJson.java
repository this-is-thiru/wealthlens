package com.thiru.wealthlens.taxengine.rules.json;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSelection;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import com.thiru.wealthlens.taxengine.rules.pack.SelectionGround;
import com.thiru.wealthlens.taxengine.rules.validate.PackValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import tools.jackson.databind.JsonNode;

/**
 * A year's rule sets: each declared in the law file's {@code ruleSets}, its members the rules that name it (W-088m1,
 * D-252). Membership is on the row, so adding a row is one file; the declaration says how a node takes the rows. A
 * row in no set loads, and is refused by {@code PackValidator}, so a pack is checked whole rather than at its first file.
 */
public final class RuleSetJson {

    private RuleSetJson() {
    }

    /** One rule's membership, as its row states it. */
    private record Membership(RuleId rule, RuleSetId set, OptionalInt position, String path) {
    }

    /** The sets of one law file, keyed by id, each with its members in the year. */
    public static Map<RuleSetId, RuleSet> ruleSets(JsonNode root, String path) {
        Map<RuleSetId, RuleSet> declared = new LinkedHashMap<>();
        for (RuleSet set : PackJson.arrayOrEmpty(root, "ruleSets", path, e -> declaration(e.node(), e.path()))) {
            if (declared.put(set.id(), set) != null) {
                throw new PackValidationException(path + " declares rule set " + set.id() + " twice");
            }
        }
        List<Membership> memberships = PackJson.array(root, "rules", path, e -> membership(e.node(), e.path())).stream()
                .flatMap(Optional::stream)
                .toList();
        Map<RuleSetId, RuleSet> withMembers = new LinkedHashMap<>();
        declared.values().forEach(set -> withMembers.put(set.id(), withMembers(set, memberships, path)));
        memberships.stream()
                .filter(membership -> !declared.containsKey(membership.set()))
                .findFirst()
                .ifPresent(membership -> {
                    throw new PackValidationException(membership.path() + " names rule set " + membership.set()
                            + ", which " + path + " does not declare");
                });
        return withMembers;
    }

    private static RuleSet declaration(JsonNode node, String path) {
        RuleSetId id = PackJson.construct(path, () -> new RuleSetId(PackJson.text(node, "id", path)));
        return new RuleSet(id, PackJson.enumValue(node, "selection", path, RuleSelection.class),
                PackJson.enumValue(node, "ground", path, SelectionGround.class), List.of());
    }

    /** The row's membership; none where it names no set, which the validator refuses for a shipped pack. */
    private static Optional<Membership> membership(JsonNode node, String path) {
        RuleId rule = new RuleId(PackJson.text(node, "ruleId", path));
        return PackJson.optionalText(node, "ruleSet", path).map(name -> {
            RuleSetId set = PackJson.construct(path, () -> new RuleSetId(name));
            JsonNode position = node.get("ruleSetPosition");
            return new Membership(rule, set, position == null || position.isNull()
                    ? OptionalInt.empty()
                    : OptionalInt.of(PackJson.integer(node, "ruleSetPosition", path)), path);
        });
    }

    /** The set with its members: by stated position where it takes every row, else by id. */
    private static RuleSet withMembers(RuleSet set, List<Membership> memberships, String path) {
        List<Membership> members = new ArrayList<>(memberships.stream()
                .filter(membership -> membership.set().equals(set.id()))
                .toList());
        boolean ordered = set.selection() == RuleSelection.ALL_THAT_APPLY;
        for (Membership member : members) {
            if (member.position().isPresent() != ordered) {
                throw new PackValidationException(member.path() + " (" + member.rule() + ") "
                        + (ordered ? "needs a ruleSetPosition: " : "may not state a ruleSetPosition: ")
                        + set.id() + " is " + set.selection());
            }
        }
        if (ordered) {
            members.sort(Comparator.comparingInt(member -> member.position().getAsInt()));
            for (int i = 1; i < members.size(); i++) {
                if (members.get(i).position().getAsInt() == members.get(i - 1).position().getAsInt()) {
                    throw new PackValidationException(path + ": rule set " + set.id() + " places "
                            + members.get(i - 1).rule() + " and " + members.get(i).rule() + " at the same position");
                }
            }
        } else {
            members.sort(Comparator.comparing(member -> member.rule().value()));
        }
        return new RuleSet(set.id(), set.selection(), set.ground(), members.stream().map(Membership::rule).toList());
    }
}
