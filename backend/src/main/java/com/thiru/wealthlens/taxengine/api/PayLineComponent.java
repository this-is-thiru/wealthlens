package com.thiru.wealthlens.taxengine.api;

/**
 * A component a pay line may carry in a year: income under the head Salaries, live in that year (W-133a). What a salary
 * workbook's labels are matched against, by its code or by the name a user sees.
 */
public record PayLineComponent(String code, String displayName) {
}
