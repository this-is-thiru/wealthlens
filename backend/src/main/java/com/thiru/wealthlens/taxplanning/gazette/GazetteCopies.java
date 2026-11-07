package com.thiru.wealthlens.taxplanning.gazette;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Fetches numbered copies from the e-Gazette, {@code <baseUrl>/<year>/<number>.pdf} (W-236 C1a). A number not yet
 * published answers 404 and is absent; any other answer that is not a copy is a failure, never read as absence, so the
 * watch's cursor is not moved past it. Only GET is used: the site answers HEAD with 404 even for a copy it holds.
 */
public record GazetteCopies(RestClient client, GazetteWatchSettings settings) {

    /** The copy at that number, or empty where none is published. */
    public Optional<GazetteCopy> fetch(int year, long number) {
        String url = settings.baseUrl() + "/" + year + "/" + number + ".pdf";
        return client.get().uri(url).exchange((request, response) -> {
            if (response.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException(url + " answered " + response.getStatusCode().value());
            }
            return Optional.of(new GazetteCopy(url, year, number, bounded(response.getBody())));
        });
    }

    /** The body, or empty once it passes the size the watch reads — read no further than one byte past it. */
    private Optional<byte[]> bounded(InputStream body) throws IOException {
        long limit = settings.maxDocumentSize().toBytes();
        byte[] bytes = body.readNBytes(Math.toIntExact(limit + 1));
        return bytes.length > limit ? Optional.empty() : Optional.of(bytes);
    }
}
