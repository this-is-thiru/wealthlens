package com.thiru.wealthlens.taxrules.pack;

import com.thiru.wealthlens.taxrules.id.RegimeCode;
import java.util.Set;

/**
 * Which regimes a rule applies in — a <b>set</b>, never a tri-state.
 *
 * <p>{@code 10-extensibility.md} A1 rates this the most expensive thing in the design to get wrong,
 * because it is in the persisted pack schema. The tempting model is {@code OLD | NEW | BOTH}, and it
 * works exactly as long as there are two regimes. With four, <em>&ldquo;applies in A, C and D&rdquo;</em>
 * is inexpressible, and the schema is already on disk.
 *
 * <p>{@link ExceptIn} is not redundant with {@link OnlyIn}. A rule written <em>&ldquo;everywhere
 * except the default regime&rdquo;</em> keeps meaning that when a fifth regime arrives; the same
 * rule written as {@code OnlyIn} of the others silently fails to apply in the new one, and nothing
 * announces it.
 */
public sealed interface RegimeApplicability {

    boolean appliesIn(RegimeCode regime);

    /** Applies in every regime, present and future. */
    record AllRegimes() implements RegimeApplicability {
        @Override
        public boolean appliesIn(RegimeCode regime) {
            return true;
        }
    }

    /** Applies only in the named regimes. A new regime does not get it. */
    record OnlyIn(Set<RegimeCode> regimes) implements RegimeApplicability {

        public OnlyIn {
            if (regimes == null || regimes.isEmpty()) {
                throw new IllegalArgumentException(
                        "OnlyIn must name at least one regime; an empty set applies nowhere");
            }
            regimes = Set.copyOf(regimes);
        }

        @Override
        public boolean appliesIn(RegimeCode regime) {
            return regimes.contains(regime);
        }
    }

    /** Applies everywhere except the named regimes. A new regime <b>does</b> get it. */
    record ExceptIn(Set<RegimeCode> regimes) implements RegimeApplicability {

        public ExceptIn {
            if (regimes == null || regimes.isEmpty()) {
                throw new IllegalArgumentException(
                        "ExceptIn must name at least one regime; use AllRegimes instead");
            }
            regimes = Set.copyOf(regimes);
        }

        @Override
        public boolean appliesIn(RegimeCode regime) {
            return !regimes.contains(regime);
        }
    }
}
