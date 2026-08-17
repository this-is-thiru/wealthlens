package com.thiru.wealthlens;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class WealthLensModulithTest {

    @Test
    void modulithStructureIsValid() {
        ApplicationModules modules = ApplicationModules.of(WealthLensApplication.class);
        modules.verify();
    }
}
