package com.thiru.wealthlens.taxplanning.gazette;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The e-Gazette client: the watch's own name as its User-Agent, and its timeouts (W-236 C4a). */
@Configuration
public class GazetteWatchConfig {

    @Bean
    public GazetteCopies gazetteCopies(GazetteWatchSettings settings) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(settings.timeout());
        requests.setReadTimeout(settings.timeout());
        RestClient client = RestClient.builder()
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.USER_AGENT, settings.userAgent())
                .build();
        return new GazetteCopies(client, settings);
    }
}
