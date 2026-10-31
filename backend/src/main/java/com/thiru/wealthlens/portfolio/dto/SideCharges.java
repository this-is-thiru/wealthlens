package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.shared.util.money.TMoney;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one side of a set of disposals was charged, as recorded: the charge engine's lines by catalogue code, and the
 * charge the user entered by hand, which carries no code (W-162a, D-375).
 *
 * <p>Facts only. Which of them reduce a capital gain is the tax side's rule data, so nothing here is marked deductible.
 *
 * @param rupeesByCode each code's amount, to the paisa; empty where no breakdown was recorded
 * @param miscRupees the hand-entered charge, to the paisa
 */
public record SideCharges(Map<String, Double> rupeesByCode, double miscRupees) {

    /** A side that bore no charge. */
    public static final SideCharges NONE = new SideCharges(Map.of(), 0);

    public SideCharges {
        if (rupeesByCode == null) {
            throw new IllegalArgumentException("A side's charges need their codes; pass an empty map for none");
        }
        Map<String, Double> scaled = new LinkedHashMap<>();
        rupeesByCode.forEach((code, rupees) -> scaled.put(code, TMoney.scale(rupees)));
        rupeesByCode = Collections.unmodifiableMap(scaled);
        miscRupees = TMoney.scale(miscRupees);
    }

    /** This side's charges and {@code other}'s, code by code. */
    public SideCharges plus(SideCharges other) {
        Map<String, Double> merged = new LinkedHashMap<>(rupeesByCode);
        other.rupeesByCode.forEach((code, rupees) -> merged.merge(code, rupees, TMoney::add));
        return new SideCharges(merged, TMoney.add(miscRupees, other.miscRupees));
    }
}
