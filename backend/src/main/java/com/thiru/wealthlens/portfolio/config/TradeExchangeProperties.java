package com.thiru.wealthlens.portfolio.config;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The exchanges a trade of each asset type may name, matched exactly as written (W-353, D-826, D-828).
 *
 * <p>Configuration rather than code, so a venue that opens or closes is a yaml edit. A type with no entry accepts no
 * exchange: a new asset type is refused until it is listed, never let through unchecked. Stored trades are not read
 * against it.
 *
 * @param accepted the exchanges each asset type may name; the application does not start without it
 */
@ConfigurationProperties(prefix = "app.portfolio.trade-exchanges")
public record TradeExchangeProperties(Map<AssetType, List<String>> accepted) {

    public TradeExchangeProperties {
        if (accepted == null || accepted.isEmpty()) {
            throw new IllegalStateException(
                    "app.portfolio.trade-exchanges.accepted is not configured, so no trade could name an exchange");
        }
        accepted = accepted.entrySet().stream()
                .collect(toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    /** The exchanges a trade of this type may name; none for a type that is not listed. */
    public List<String> acceptedFor(AssetType assetType) {
        return accepted.getOrDefault(assetType, List.of());
    }
}
