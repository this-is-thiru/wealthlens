package com.thiru.wealthlens.taxengine.rules.filing;

import java.util.List;

/**
 * Cells a schedule repeats once for each item of a kind — each house property, as Schedule HP repeats item 1 "for each
 * property" (W-093a C2a, D-658). A field's path is printed under the group's, with the item's number from 1:
 * {@code HP.1[2].a} is item 1a of the second property.
 *
 * @param item the kind of item it repeats over, as the computation reports items, such as {@code HOUSE_PROPERTY}
 * @param path the group's path in the form, such as {@code HP.1}
 * @param fields the cells repeated for each item
 */
public record ScheduleGroup(String item, String path, List<ScheduleField> fields) {

    public ScheduleGroup {
        if (item == null || item.isBlank() || path == null || path.isBlank()) {
            throw new IllegalArgumentException("A schedule group names the kind of item it repeats over and its path");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("The group " + path + " repeats no cell");
        }
        fields = List.copyOf(fields);
    }
}
