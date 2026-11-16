package com.thiru.wealthlens.taxplanning.documents.ingest;

/**
 * Where on the page a value was read, so a person can be shown its source ({@code DR 05} M4). The box is in the page's own
 * units, from its top left.
 */
public record FieldLocation(int pageNumber, double left, double top, double width, double height) {

    public FieldLocation {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("A page number starts at 1");
        }
        if (left < 0 || top < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("A location's box cannot be negative");
        }
    }
}
