package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class OrderTimeQuantity {
	private Double quantity;
	private LocalDateTime orderExecutionTime;
	private String timezoneId = TLocalDate.TIME_ZONE_IST;
}
