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

		double purchaseBrokerCharge = perUnit(assetEntity.getBrokerCharges(), assetEntity.getQuantity(), sellQuantity);
		double purchaseMiscCharge = perUnit(assetEntity.getMiscCharges(), assetEntity.getQuantity(), sellQuantity);

		AssetContext purchaseContext = AssetContext.from();
		purchaseContext.setPrice(purchasePrice);
		purchaseContext.setQuantity(assetEntity.getQuantity());
		purchaseContext.setTransactionDate(purchaseDate);
		purchaseContext.setAssetType(assetEntity.getAssetType());
		purchaseContext.setBrokerCharges(purchaseBrokerCharge);
		purchaseContext.setMiscCharges(purchaseMiscCharge);

		double sellBrokerCharge = perUnit(assetRequest.getBrokerCharges(), assetRequest.getQuantity(), sellQuantity);
		double sellMiscCharge = perUnit(assetRequest.getMiscCharges(), assetRequest.getQuantity(), sellQuantity);

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

	/**
	 * A charge's share of a partial quantity, guarding the divide.
	 *
	 * <p>This used to be a bare {@code charges / quantity * sellQuantity}. On a zero-quantity asset
	 * that is {@code 10.0 / 0.0} — <b>{@code Infinity}</b>, which was then accumulated into the
	 * period's totals and written to the document. It never threw, so it looked handled; in fact it
	 * poisoned the whole profit-and-loss record, because {@code Infinity + anything} stays
	 * {@code Infinity} for every trade that follows, forever.
	 *
	 * <p>Found by TL-8: canonicalising the accumulation turned the silent corruption into a loud
	 * failure, which is the argument for canonicalising in one place rather than adding doubles
	 * inline.
	 *
	 * <p>Zero quantity means zero allocated charge. There is nothing to spread a charge over.
	 */
	private static double perUnit(double charges, Double quantity, double sellQuantity) {
		if (quantity == null || quantity == 0) {
			return 0;
		}
		return (charges / quantity) * sellQuantity;
	}
}
