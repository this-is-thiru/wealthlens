package com.thiru.wealthlens.taxplanning.gazette;

import java.util.Optional;

/**
 * One copy fetched from the e-Gazette: its bytes, or none where it was larger than the watch reads (W-236 E2a).
 *
 * @param url where it was fetched from
 * @param year the year folder
 * @param number its number
 * @param pdf the copy, empty where it was too large to read
 */
public record GazetteCopy(String url, int year, long number, Optional<byte[]> pdf) {
}
