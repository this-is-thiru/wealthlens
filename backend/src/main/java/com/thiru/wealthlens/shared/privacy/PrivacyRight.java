package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * One right the Digital Personal Data Protection Act, 2023 gives a user, and the means of exercising it here (Rules
 * r.14(1)(a); W-237a). Wording is configuration: what the app offers changes as requests gain endpoints, not the law.
 *
 * @param right what the user may ask for
 * @param section where the Act gives it
 * @param means how to ask
 * @param endpoints the self-service requests that exercise it, empty where it is asked for by writing to the contact
 */
public record PrivacyRight(String right, String section, String means, List<String> endpoints) {

    public PrivacyRight {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }
}
