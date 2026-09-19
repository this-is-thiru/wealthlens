package com.thiru.wealthlens.taxrules.regime;

/**
 * A prescribed form, by the number the taxpayer would look for.
 *
 * @param number as printed, e.g. {@code "10-IEA"} or {@code "124"}
 * @param displayName what it is for, shown alongside the number
 */
public record FormRef(String number, String displayName) {

    public FormRef {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("A form reference must carry its number");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Form " + number + " must say what it is for; a bare number is not an instruction");
        }
    }
}
