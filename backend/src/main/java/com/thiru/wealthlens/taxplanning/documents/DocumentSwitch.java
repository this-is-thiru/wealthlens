package com.thiru.wealthlens.taxplanning.documents;

import com.thiru.wealthlens.shared.exception.ServiceUnavailableException;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentSettings;

/**
 * The documents switch, {@code app.tax.documents.enabled} (W-131d1, D-765 C2a): off in every profile yaml until uploads are
 * scanned (W-131e) and the parser isolated (W-131f). Checked at each entry point, so a switched-off flow writes nothing.
 */
public final class DocumentSwitch {

    private static final String OFF = "Uploading documents is switched off (app.tax.documents.enabled=false)";

    private DocumentSwitch() {
    }

    public static void requireEnabled(DocumentSettings settings) {
        if (!settings.enabled()) {
            throw new ServiceUnavailableException(OFF);
        }
    }
}
