package com.thiru.wealthlens.taxplanning.documents.assembly;

import java.util.List;

/** A year's certificates read together: how they carry Part C, and what to look at or act on (W-129). */
public record Assembly(AssemblyMode mode, List<AssemblyNotice> notices) {

    public Assembly {
        if (mode == null || notices == null) {
            throw new IllegalArgumentException("An assembly states its mode and its notices (or none)");
        }
        notices = List.copyOf(notices);
    }
}
