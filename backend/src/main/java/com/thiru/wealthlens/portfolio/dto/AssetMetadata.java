package com.thiru.wealthlens.portfolio.dto;

import com.thiru.wealthlens.shared.dto.enums.AccountType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(staticName = "from")
public class AssetMetadata {
	private AccountType accountType;
	private String accountHolder;
}
