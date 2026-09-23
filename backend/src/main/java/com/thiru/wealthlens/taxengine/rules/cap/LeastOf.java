package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The least of several named limbs — the shape behind the house rent allowance exemption, gratuity,
 * leave encashment, retrenchment compensation and the standard deduction's salary cap.
 *
 * <p>The names are the point. A cap that returns only &#8377;2,28,000 has answered the question;
 * one that also says <em>&ldquo;rent less ten per cent of salary&rdquo;</em> has explained it, and
 * the explanation is what a taxpayer can act on. Knowing that limb (b) bound is what makes
 * <em>&ldquo;paying more rent would help; a larger allowance alone would not&rdquo;</em> a true
 * statement rather than a guess — see {@code 03-advisory.md} A4.4.
 *
 * <h2>Ties</h2>
 *
 * <p>The <b>first declared limb wins</b>. The statute lists limbs in order — (a), (b), (c) — and an
 * arbitrary winner would make a trace line, a golden test and a snapshot non-deterministic for
 * inputs that are perfectly ordinary.
 *
 * <h2>This shape does not floor at zero</h2>
 *
 * <p>Limb (b) of the house rent allowance is {@code rent paid − 10% of salary}, which is negative
 * whenever rent is below a tenth of salary. {@code 06-algorithms.md} G1.3 floors the result at the
 * <em>caller</em>, not here, and that is deliberate: a node that receives &#8722;&#8377;40,000 knows
 * the taxpayer is in a position where renting claims nothing, and a node that receives zero cannot
 * tell that from a taxpayer who pays no rent at all.
 *
 * <h2>Where the limbs come from</h2>
 *
 * <p>A limb is a {@link CapShape}, so it may be declared in the pack — limb (c) is a
 * {@link PercentOfBase} — or computed by a node and handed in as a {@link Fixed}. Limb (b) is a
 * subtraction, which is not one of the six shapes, so the node builds it. The shape stays closed;
 * only its inputs are assembled later.
 *
 * @param limbs at least one, in the order the statute lists them, with distinct names
 */
public record LeastOf(List<Limb> limbs) implements CapShape {

    /**
     * One alternative, and the name it reports when it wins.
     *
     * @param name as the rulebook words it, because it reaches the taxpayer verbatim
     */
    public record Limb(String name, CapShape shape) {

        public Limb {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A limb must be named; a winning limb with no name explains nothing");
            }
            if (shape == null) {
                throw new IllegalArgumentException("Limb \"" + name + "\" has no shape");
            }
        }
    }

    public LeastOf {
        if (limbs == null || limbs.isEmpty()) {
            throw new IllegalArgumentException("A LeastOf must have at least one limb");
        }
        limbs = List.copyOf(limbs);

        Set<String> seen = new HashSet<>();
        for (Limb limb : limbs) {
            if (!seen.add(limb.name())) {
                throw new IllegalArgumentException(
                        "Two limbs are both named \"" + limb.name() + "\"; then limitedBy cannot say which one bound");
            }
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        Limb winner = limbs.getFirst();
        Money least = winner.shape().apply(ctx).value();

        for (Limb limb : limbs.subList(1, limbs.size())) {
            Money value = limb.shape().apply(ctx).value();
            if (value.isLessThan(least)) {
                least = value;
                winner = limb;
            }
        }
        return new CapResult(least, winner.name());
    }

    @Override
    public String shapeName() {
        return "least of " + limbs.size() + " limbs";
    }
}
