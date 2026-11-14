package com.thiru.wealthlens.shared.privacy;

/**
 * One part of the itemised description of personal data the notice before consent gives (DPDP Rules r.3(b)(i); W-250). Each
 * {@link PersonalDataHolder} names the part that describes what it stores, so a store added without a description is found.
 *
 * @param part the key a holder names ({@link PersonalDataHolder#noticePart()})
 * @param description what that part of the app stores about the person, in plain language
 */
public record PersonalDataItem(String part, String description) {

    public PersonalDataItem {
        if (part == null || part.isBlank() || description == null || description.isBlank()) {
            throw new IllegalStateException("each entry of app.privacy.consent.personal-data must name its part and describe the"
                    + " personal data it holds (DPDP Rules r.3(b)(i))");
        }
    }
}
