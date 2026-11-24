package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Compares by value (W-337e): held inside {@code AssetEntity.orderTimeQuantities}, a JSON-converted column Hibernate
 * dirty-checks by round-tripping through the converter and comparing with {@code equals}; without it, a freshly
 * deserialized list is never equal to the one it was loaded from, so every lot carrying order times would be
 * rewritten, audit trail and all, on any flush that merely loaded it.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class OrderTimeQuantity {
	private Double quantity;
	private LocalDateTime orderExecutionTime;
	private String timezoneId = TLocalDate.TIME_ZONE_IST;
}
