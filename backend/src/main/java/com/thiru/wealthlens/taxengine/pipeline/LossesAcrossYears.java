package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * Each head's loss brought into the year and left to carry out of it, as amounts — r.164(2)(b) and (d) of the
 * Income-tax Rules, 2026 bar a form on either, and before W-090c1b2 (D-528) both were only trace text.
 *
 * <p>A loss is under the head its carry-forward row sets it off against. Brought forward is what the law still carries
 * into the year — within its span and, where the row asks, determined in a return filed in time — before set-off,
 * including a portion the new regime then deems given full effect to. To carry forward is this year's loss left and
 * each carried-in loss still carried; the year's own capital loss is counted though carrying it also needs this
 * year's return filed in time (S2 s.121), which no computation can know.
 *
 * @param broughtForward each head's loss brought forward, nil or more; a head the set-off did not read is absent
 * @param toCarryForward each head's loss left to carry forward, nil or more; a head the set-off did not read is absent
 */
public record LossesAcrossYears(Map<HeadCode, Money> broughtForward, Map<HeadCode, Money> toCarryForward) {

    /** No losses recorded: a set-off value built without them, whose reads throw for every head. */
    public static final LossesAcrossYears NONE = new LossesAcrossYears(Map.of(), Map.of());

    public LossesAcrossYears {
        if (broughtForward == null || toCarryForward == null) {
            throw new IllegalArgumentException("LossesAcrossYears needs both maps; pass empty ones");
        }
        requireNotNegative(broughtForward, "brought forward");
        requireNotNegative(toCarryForward, "to carry forward");
        broughtForward = Map.copyOf(broughtForward);
        toCarryForward = Map.copyOf(toCarryForward);
    }

    private static void requireNotNegative(Map<HeadCode, Money> losses, String what) {
        losses.forEach((head, loss) -> {
            if (loss.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A loss " + what + " is nil or more, but under " + head + " was "
                        + loss);
            }
        });
    }
}
