package com.thiru.wealthlens.portfolio.dto.context;

import com.thiru.wealthlens.portfolio.dto.AssetMetadata;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor(staticName = "from")
@EqualsAndHashCode
@Data
public class ProfitAndLossContext {
	private AssetContext purchaseContext;
	private AssetContext sellContext;
	private AssetMetadata metadata;

	/**
	 * Profit and loss context while selling the asset
	 */
	public static ProfitAndLossContext from(AssetEntity assetEntity, AssetRequest assetRequest, double sellQuantity) {
		double purchasePrice = assetEntity.getPrice();
		LocalDate purchaseDate = assetEntity.getTransactionDate();

		double purchaseBrokerCharge = (assetEntity.getBrokerCharges() / assetEntity.getQuantity()) * sellQuantity;
		double purchaseMiscCharge = (assetEntity.getMiscCharges() / assetEntity.getQuantity()) * sellQuantity;

		AssetContext purchaseContext = AssetContext.from();
		purchaseContext.setPrice(purchasePrice);
		purchaseContext.setQuantity(assetEntity.getQuantity());
		purchaseContext.setTransactionDate(purchaseDate);
		purchaseContext.setAssetType(assetEntity.getAssetType());
		purchaseContext.setBrokerCharges(purchaseBrokerCharge);
		purchaseContext.setMiscCharges(purchaseMiscCharge);

		double sellBrokerCharge = (assetRequest.getBrokerCharges() / assetRequest.getQuantity()) * sellQuantity;
		double sellMiscCharge = (assetRequest.getMiscCharges() / assetRequest.getQuantity()) * sellQuantity;

		AssetContext sellContext = AssetContext.from();
		sellContext.setPrice(assetRequest.getPrice());
		sellContext.setQuantity(sellQuantity);
		sellContext.setTransactionDate(assetRequest.getTransactionDate());
		sellContext.setAssetType(assetRequest.getAssetType());
		sellContext.setBrokerCharges(sellBrokerCharge);
		sellContext.setMiscCharges(sellMiscCharge);

		AssetMetadata metadata = AssetMetadata.from();
		metadata.setAccountType(assetRequest.getAccountType());
		metadata.setAccountHolder(assetRequest.getAccountHolder());

		ProfitAndLossContext profitAndLossContext = ProfitAndLossContext.from();
		profitAndLossContext.setPurchaseContext(purchaseContext);
		profitAndLossContext.setSellContext(sellContext);
		profitAndLossContext.setMetadata(metadata);
		return profitAndLossContext;
	}
}
