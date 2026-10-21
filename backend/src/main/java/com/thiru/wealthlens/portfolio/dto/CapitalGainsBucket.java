package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.SttEvidence;
import com.thiru.wealthlens.shared.util.money.TMoney;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One year's realised gain for one classification of one kind of asset, as {@code portfolio} publishes it
 * ({@code 13-edge-cases.md} R-10; D-237).
 *
 * <p>Gross, and never a tax: {@code portfolio} owns which disposals happened and what they made, the tax side owns
 * the rate. So the bucket carries what the rate depends on — the asset and its sub-class, since listed equity and
 * equity-oriented funds are charged apart from everything else — and what the records say of securities transaction tax
 * on the buy and on the sale, paid, not paid or unknown, which the special rates are conditioned on (S2 ss.196, 198;
 * D-241, D-292), and where and how each side was traded, which decides whether STT was chargeable where no record
 * says (D-295); and how and when the
 * lots were acquired, the facts any waiver of that condition turns on (S.O. 5054(E); W-088f, D-249) — facts only, so a
 * change in the law is a change in the tax side's rule data, never here; no rate basis of its own. Every classification
 * is published, {@code UNCLASSIFIED} and {@code SPECULATIVE} included, so the tax side can block on the one and route
 * the other ({@code 07-architecture.md} X1.3) rather than never seeing them.
 *
 * @param classification how the disposals were classified
 * @param assetType what was disposed of
 * @param instrumentSubClass the scheme's or bond's sub-class, where the instrument registry has given one
 * @param sttOnAcquisition what the records say of securities transaction tax on the buys these gains came from
 * @param sttOnTransfer what they say of it on the sales
 * @param acquisitionVenue where and how the buys were traded, as recorded
 * @param transferVenue where and how the sales were traded, as recorded
 * @param acquisition how the lots these gains came from were acquired; {@code UNRECORDED} where no row says
 * @param acquiredOn the date those lots were acquired
 * @param transferredOn the date the disposals in this bucket were made — with {@code acquiredOn}, what the holding
 *         period is counted from and to on the tax side (W-088k2, D-304)
 * @param gainRupees the gross gain, a loss negative, canonical to paise
 */
public record CapitalGainsBucket(
        CapitalGainsType classification,
        AssetType assetType,
        Optional<String> instrumentSubClass,
        SttEvidence sttOnAcquisition,
        SttEvidence sttOnTransfer,
        TradeVenue acquisitionVenue,
        TradeVenue transferVenue,
        Acquisition acquisition,
        LocalDate acquiredOn,
        LocalDate transferredOn,
        double gainRupees) {

    public CapitalGainsBucket {
        if (classification == null || assetType == null || instrumentSubClass == null || sttOnAcquisition == null
                || sttOnTransfer == null || acquisitionVenue == null || transferVenue == null || acquisition == null
                || acquiredOn == null || transferredOn == null) {
            throw new IllegalArgumentException("A capital-gains bucket needs its classification, asset type, STT"
                    + " evidence, venues, acquisition, acquisition date and transfer date; pass Optional.empty() for an instrument"
                    + " without a sub-class, UNKNOWN for STT with no record and TradeVenue.UNRECORDED for no venue");
        }
        gainRupees = TMoney.scale(gainRupees);
    }
}
