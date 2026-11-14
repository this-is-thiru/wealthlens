package com.thiru.wealthlens.corporate.dto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stock split's ratio, {@code new:old}: {@code 2:1} doubles the shares and halves their price, so a lot's cost is unchanged
 * (W-213, W-216, D-438). The one place a split's ratio is read, at entry and when applied, so the two cannot disagree.
 *
 * @param newShares the shares held after, for {@code oldShares} held before
 * @param oldShares the shares held before
 */
public record SplitRatio(int newShares, int oldShares) {

    private static final Pattern FORMAT = Pattern.compile("([1-9][0-9]{0,8}):([1-9][0-9]{0,8})");

    /**
     * Reads {@code text}.
     *
     * @throws IllegalArgumentException if it is not two whole numbers above nil, as {@code 2:1}
     */
    public static SplitRatio parse(String text) {
        Matcher parts = text == null ? FORMAT.matcher("") : FORMAT.matcher(text);
        if (!parts.matches()) {
            throw new IllegalArgumentException("A stock split's ratio is new shares to old shares, two whole numbers above"
                    + " nil such as 2:1, but it was " + text);
        }
        return new SplitRatio(Integer.parseInt(parts.group(1)), Integer.parseInt(parts.group(2)));
    }

    /** The quantity of a lot of {@code quantity} after the split. */
    public double quantityAfter(double quantity) {
        return quantity * newShares / oldShares;
    }

    /** The price of a share priced {@code price} after the split. */
    public double priceAfter(double price) {
        return price * oldShares / newShares;
    }
}
