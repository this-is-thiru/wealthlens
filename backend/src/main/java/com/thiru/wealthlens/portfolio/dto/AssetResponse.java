package com.thiru.wealthlens.portfolio.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.model.AuditMetadataDto;
import com.thiru.wealthlens.shared.dto.model.AuditableResponse;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class AssetResponse implements AuditableResponse {
	private String email;
	private String stockCode;
	private String stockName;
	private String exchangeName;
	private BrokerName brokerName;
	private AssetType assetType;
	private LocalDate maturityDate;
	private double price;
	private Double quantity;
	private Double totalQuantity;
	private double totalValue;
	private TransactionType transactionType;
	private String orderId;
	// remove this as we use orderTimeQuantity
	private Instant orderExecutionTime;
	private List<OrderTimeQuantity> orderTimeQuantities = new ArrayList<>();
	private String timezoneId = TLocalDate.TIME_ZONE_IST;
	private String accountType;
	private String accountHolder;
	private String remarks;
	private double brokerCharges;
	private double miscCharges;
	private String comment;
	private CorporateActionType corporateActionType;
	private List<String> buyTransactionIds = new ArrayList<>();
	private List<String> sellTransactionIds = new ArrayList<>();
	List<CorporateActionDto> corporateActions = new ArrayList<>();

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	private LocalDate transactionDate;

	private Map<String, Double> transactionQuantities;

	private AuditMetadataDto auditMetadata;
}
