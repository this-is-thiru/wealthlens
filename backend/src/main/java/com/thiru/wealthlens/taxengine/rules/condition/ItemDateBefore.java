package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import java.time.LocalDate;

/**
 * A dated fact of the item being computed falls before a date — how a row says which acquisitions a notification
 * covers by when they were made, such as S.O. 5054(E)'s "before the 1st day of October, 2004" (W-088f2b, D-291).
 *
 * <p>Strict, as the law's "before" is: the day itself is not before it. This dates a fact of the item, which decides
 * whether the row applies; it does not date the rule, whose validity stays on its versions (V-2).
 *
 * @param fact the item's dated fact, such as when a lot was acquired
 * @param date the first day that is not before
 */
public record ItemDateBefore(ItemFactId fact, LocalDate date) implements Condition {

    public ItemDateBefore {
        if (fact == null || date == null) {
            throw new IllegalArgumentException("ItemDateBefore needs a fact and a date");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.item().date(fact).isBefore(date);
    }

    @Override
    public String describe() {
        return fact + " is before " + date;
    }
}
