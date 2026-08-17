package com.thiru.wealthlens.portfolio.dto.context;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(staticName = "from")
public class AssetContext {
	private String name;
	private double price;
	private double quantity;
	private LocalDate transactionDate;
	private AssetType assetType;
	private double brokerCharges;
	private double miscCharges;
}
