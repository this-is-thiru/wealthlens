package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.id.FurnishingManner;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.List;

/**
 * One manner in which a form may be furnished, and who may use it — r.164(12)'s Table, where paper is open only to
 * some (W-090b).
 *
 * @param manner the manner
 * @param conditions all of which must hold for the manner to be open; empty where it is open to everyone the form is
 * @param provenance where the manner was read
 */
public record FurnishingOption(FurnishingManner manner, List<Condition> conditions, Provenance provenance) {

    public FurnishingOption {
        if (manner == null || provenance == null) {
            throw new IllegalArgumentException("A manner of furnishing must name itself and its source");
        }
        if (conditions == null) {
            throw new IllegalArgumentException(
                    manner + ": conditions may not be null; pass an empty list where the manner is open to all");
        }
        conditions = List.copyOf(conditions);
    }
}
