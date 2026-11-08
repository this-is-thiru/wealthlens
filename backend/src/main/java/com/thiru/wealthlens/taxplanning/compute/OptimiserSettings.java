package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The optimiser's tuning knob ({@code DR 14} O8, O10; W-238b C2a): how many components its exhaustive check may cover.
 * O8 meets the time budget by lowering it, never by giving up exact pricing, so a request may ask for less and never for
 * more. It changes how far the answer is checked, never a computed figure (O10).
 *
 * @param exhaustiveBreadth the most components a request's exhaustive check may cover
 */
@ConfigurationProperties(prefix = "app.tax.optimiser")
public record OptimiserSettings(int exhaustiveBreadth) {

    public OptimiserSettings {
        if (exhaustiveBreadth < 1) {
            throw new IllegalArgumentException("app.tax.optimiser.exhaustive-breadth must be at least 1, not "
                    + exhaustiveBreadth);
        }
    }

    /**
     * The breadth a request runs at: what it asks for, or the setting when it asks for none.
     *
     * @throws BadRequestException if it asks for more than the setting
     */
    public int breadthFor(Optional<Integer> asked) {
        int breadth = asked.orElse(exhaustiveBreadth);
        if (breadth > exhaustiveBreadth) {
            throw new BadRequestException("The exhaustive check can cover at most " + exhaustiveBreadth
                    + " components here, not " + breadth);
        }
        return breadth;
    }
}
