package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import java.util.List;

/**
 * Rows a node evaluates together, and how one is chosen — declared in {@code law/rule-sets.json}, with each rule row
 * naming its set (W-088m1, D-252). The node names the set; which row applies is the data's to say.
 *
 * @param id the set's identity
 * @param selection how many rows a node takes
 * @param ground what decides whether a row applies
 * @param members the year's rows of the set, in its stated order where {@link RuleSelection#ALL_THAT_APPLY}, else by id;
 *     empty where the year holds none
 */
public record RuleSet(RuleSetId id, RuleSelection selection, SelectionGround ground, List<RuleId> members) {

    public RuleSet {
        if (id == null || selection == null || ground == null || members == null) {
            throw new IllegalArgumentException("A rule set needs its id, selection, ground and members");
        }
        members = List.copyOf(members);
        if (members.stream().distinct().count() != members.size()) {
            throw new IllegalArgumentException("Rule set " + id + " lists a row twice: " + members);
        }
    }
}
