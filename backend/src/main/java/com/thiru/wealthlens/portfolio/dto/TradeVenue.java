package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.util.Collection;
import java.util.Optional;

/**
 * Where and how one side of a disposal was traded, as recorded — the facts securities transaction tax turns on: whether
 * the transaction was entered into in a recognised stock exchange and settled by delivery (Finance (No. 2) Act, 2004
 * s.98; W-088j2b, D-295).
 *
 * <p>Facts only. The exchange is the string the trade was recorded with, passed as is: which strings name a recognised
 * stock exchange is the tax side's rule data. The segment is the one recorded, which the entry API defaults to
 * {@code DELIVERY} unless the client says otherwise — so {@code DELIVERY} is also what a trade that said nothing
 * carries; nothing stored tells the two apart (D-295).
 *
 * @param exchange the exchange the side was recorded on; empty where the row does not say
 * @param segment the segment the side was recorded in; empty where the row does not say
 */
public record TradeVenue(Optional<String> exchange, Optional<TradeSegment> segment) {

    /** A side whose record says nothing of where or how it was traded — rows written before W-088j2b. */
    public static final TradeVenue UNRECORDED = new TradeVenue(Optional.empty(), Optional.empty());

    public TradeVenue {
        if (exchange == null || segment == null) {
            throw new IllegalArgumentException("A trade venue needs its exchange and segment; pass Optional.empty()"
                    + " for one not recorded");
        }
    }

    /** As stored on a {@code trade_outcomes} row or a trade; a missing value is not recorded. */
    public static TradeVenue stored(String exchange, TradeSegment segment) {
        return new TradeVenue(Optional.ofNullable(exchange), Optional.ofNullable(segment));
    }

    /** What merged buys share, each fact apart: one they disagree on is not recorded for the lot. */
    public static TradeVenue common(Collection<TradeVenue> venues) {
        return new TradeVenue(only(venues.stream().map(TradeVenue::exchange).toList()),
                only(venues.stream().map(TradeVenue::segment).toList()));
    }

    private static <T> Optional<T> only(Collection<Optional<T>> values) {
        return values.stream().distinct().count() == 1 ? values.iterator().next() : Optional.empty();
    }
}
