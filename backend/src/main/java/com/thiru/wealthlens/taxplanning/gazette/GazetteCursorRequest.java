package com.thiru.wealthlens.taxplanning.gazette;

/**
 * Where the Gazette watch is to start: the year folder and the last number already read in it (W-236 D1a).
 *
 * @param year the year folder of {@code lastNumber}
 * @param lastNumber the number the watch reads after
 */
public record GazetteCursorRequest(int year, long lastNumber) {
}
