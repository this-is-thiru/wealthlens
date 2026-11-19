package com.thiru.wealthlens.taxplanning.gazette;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * How the Gazette watch reads the e-Gazette (W-236). The words that make a copy a lead are configuration, not code: an
 * instrument's wording is a fact about the Gazette, and a change in it is a change here, not a release.
 *
 * @param enabled whether the daily run happens at all; off in every profile until the owner turns it on
 * @param baseUrl the directory the copies sit under, {@code <baseUrl>/<year>/<number>.pdf}
 * @param userAgent what the watch calls itself — a name, never a person's address
 * @param timeout how long one request may take to connect or to answer
 * @param pause the wait between two requests
 * @param maxNumbersPerRun the most numbers one run reads
 * @param missesToStop how many missing numbers in a row mean the end of what is published
 * @param maxDocumentSize a copy larger than this is not read but recorded as a lead to open by hand
 * @param leadWhenAny a copy naming any of these is a lead
 * @param leadWhenAll a copy naming all of these is a lead
 */
@ConfigurationProperties(prefix = "app.tax.gazette-watch")
public record GazetteWatchSettings(boolean enabled, String baseUrl, String userAgent, Duration timeout, Duration pause,
        int maxNumbersPerRun, int missesToStop, DataSize maxDocumentSize, List<String> leadWhenAny,
        List<String> leadWhenAll) {

    public GazetteWatchSettings {
        leadWhenAny = List.copyOf(leadWhenAny);
        leadWhenAll = List.copyOf(leadWhenAll);
    }
}
