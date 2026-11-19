package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;

/**
 * Income from house property: the head, and each property's figures as the return lays them out (W-093a C1a, D-658).
 * The head is what set-off reads; the properties are what ITR-2's Schedule HP prints, one group per property.
 *
 * @param total the head's income, a loss below nil
 * @param properties each property's figures, in input order; empty where none is entered
 */
public record HousePropertyHead(Money total, List<PropertyFigures> properties) implements NodeValue {

    /**
     * One property's figures (ITR-2 Schedule HP item 1, 1a – 1k). The rent compared with the lettable value is net of
     * rent not realised (1961 s.23(1) Explanation), so where the rent is the measure the unrealised rent is shown apart
     * and added back to the gross, and where the lettable value is the measure none is shown; either way 1a – 1b – 1c is
     * the annual value. The share is applied to each figure, and 1g and 1j are taken as differences, so the figures add
     * to the property's income to the paisa.
     *
     * @param position the property's place in the input, from 0
     * @param gross 1a, the gross rent or lettable value
     * @param unrealisedRent 1b, the rent that cannot be realised
     * @param localTaxes 1c, the tax paid to local authorities
     * @param annualValue 1e, the annual value; nil for an own residence
     * @param ownShare 1f, the owner's share of the annual value
     * @param standardDeduction 1g, thirty per cent of the share
     * @param interest 1h, the interest deducted on borrowed capital — an own residence's share of the capped aggregate
     * @param arrears 1j, arrears or unrealised rent received, less thirty per cent, the owner's share
     * @param income 1k, the property's income, a loss below nil
     */
    public record PropertyFigures(int position, Money gross, Money unrealisedRent, Money localTaxes, Money annualValue,
            Money ownShare, Money standardDeduction, Money interest, Money arrears, Money income) {
    }

    public HousePropertyHead {
        if (total == null || properties == null) {
            throw new IllegalArgumentException("A house-property head needs its total and its properties, empty for none");
        }
        properties = List.copyOf(properties);
    }

    @Override
    public Money asMoney() {
        return total;
    }
}
