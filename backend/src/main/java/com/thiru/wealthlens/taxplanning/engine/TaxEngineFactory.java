package com.thiru.wealthlens.taxplanning.engine;

import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class TaxEngineFactory {

    private final NewRegimeTaxEngine newRegimeEngine;
    private final OldRegimeTaxEngine oldRegimeEngine;

    public TaxEngine getEngine(RegimeType regimeType) {
        return switch (regimeType) {
            case NEW_REGIME -> newRegimeEngine;
            case OLD_REGIME -> oldRegimeEngine;
            default -> throw new IllegalArgumentException("Unknown regime type: " + regimeType);
        };
    }

    public TaxEngine getNewRegimeEngine() {
        return newRegimeEngine;
    }

    public TaxEngine getOldRegimeEngine() {
        return oldRegimeEngine;
    }
}
