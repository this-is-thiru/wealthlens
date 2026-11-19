package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * What the notice before consent adds to the rights notice (Digital Personal Data Protection Act, 2023 s.5(1); Rules r.3;
 * W-250): the personal data, item by item, the purpose, how consent is withdrawn and how a complaint reaches the Board. Wording
 * is configuration, so a change of wording is a change of data, and a start without any of it is refused.
 *
 * @param personalData the itemised description of the personal data (r.3(b)(i))
 * @param purpose the purpose and the services it enables (r.3(b)(ii))
 * @param withdrawal how consent is withdrawn (Act s.6(4); r.3(c)(i))
 * @param boardComplaint how a complaint is made to the Data Protection Board of India (Act s.5(1)(iii); r.3(c)(iii))
 */
public record ConsentNotice(List<PersonalDataItem> personalData, String purpose, String withdrawal, String boardComplaint) {

    public ConsentNotice {
        if (personalData == null || personalData.isEmpty()) {
            throw new IllegalStateException("app.privacy.consent.personal-data must itemise the personal data held (DPDP Rules"
                    + " r.3(b)(i))");
        }
        requireText(purpose, "app.privacy.consent.purpose must state the purpose of the processing (DPDP Rules r.3(b)(ii))");
        requireText(withdrawal, "app.privacy.consent.withdrawal must state how consent is withdrawn (DPDP Rules r.3(c)(i))");
        requireText(boardComplaint, "app.privacy.consent.board-complaint must state how a complaint reaches the Board (DPDP"
                + " Rules r.3(c)(iii))");
        personalData = List.copyOf(personalData);
    }

    private static void requireText(String text, String refusal) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException(refusal);
        }
    }
}
