package com.thiru.wealthlens.brokercharges.engine;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Dispatches a rule's basis to the implementation that serves it.
 *
 * <p>Spring injects every {@link ChargeCalculator} bean, so adding a basis is a new component and
 * nothing else — no existing class is edited.
 *
 * <p>The constructor's real job is failing loudly. A {@link ChargeBasis} constant with no
 * calculator is a trap for whoever first writes a rule using it: the rate card would validate, the
 * application would start, and the charge would simply go missing when a trade was priced. Two
 * calculators claiming one basis is worse still, resolving arbitrarily by bean ordering. Both are
 * refused at startup.
 */
@Component
public class ChargeCalculatorRegistry {

    private final Map<ChargeBasis, ChargeCalculator> byBasis = new EnumMap<>(ChargeBasis.class);

    public ChargeCalculatorRegistry(List<ChargeCalculator> calculators) {
        List<String> duplicates = new ArrayList<>();
        for (ChargeCalculator calculator : calculators) {
            ChargeCalculator existing = byBasis.putIfAbsent(calculator.basis(), calculator);
            if (existing != null) {
                duplicates.add(calculator.basis().name());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "More than one charge calculator claims the same basis: " + String.join(", ", duplicates));
        }

        List<String> unserved = new ArrayList<>();
        for (ChargeBasis basis : ChargeBasis.values()) {
            if (!byBasis.containsKey(basis)) {
                unserved.add(basis.name());
            }
        }
        if (!unserved.isEmpty()) {
            throw new IllegalStateException(
                    "No charge calculator registered for basis: " + String.join(", ", unserved));
        }
    }

    /** The calculator for a basis. Never null — the constructor guarantees every basis is served. */
    public ChargeCalculator get(ChargeBasis basis) {
        if (basis == null) {
            throw new IllegalArgumentException("Charge basis must not be null");
        }
        return byBasis.get(basis);
    }
}
