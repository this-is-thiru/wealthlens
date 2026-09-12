package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "transactions")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TransactionEntity implements AuditableEntity {

	@JsonIgnore
	@MongoId
	private String id;

	@Field("email")
	private String email;

	@Field("stock_code")
	private String stockCode;

	@Field("stock_name")
	private String stockName;

	@Field("exchange_name")
	private String exchangeName;

	@Field(name = "broker_name", targetType = FieldType.STRING)
	private BrokerName brokerName;

	@Field("price")
	private double price;

	@Field("quantity")
	private Double quantity;

	@Field("total_value")
	private double totalValue;


	/** Delivery for everything recorded before Chunk 10b; there was no segment concept until then. */

	@Field(name = "segment", targetType = FieldType.STRING)

	private TradeSegment segment = TradeSegment.DELIVERY;

	@Field("broker_charges")
	private double brokerCharges;

	@Field("misc_charges")
	private double miscCharges;

	@Field("comment")
	private String comment;

	@Field(name = "asset_type", targetType = FieldType.STRING)
	private AssetType assetType;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	@Field("maturity_date")
	private LocalDate maturityDate;

	@Field("order_id")
	private String orderId;

	@Field("order_execution_time")
	private LocalDateTime orderExecutionTime;

	@Field("timezone_id")
	private String timezoneId = TLocalDate.TIME_ZONE_IST;

	@Field(name = "account_type", targetType = FieldType.STRING)
	private AccountType accountType;

	@Field("account_holder")
	private String accountHolder;

	@Field(name = "transaction_type", targetType = FieldType.STRING)
	private TransactionType transactionType;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	@Field("transaction_date")
	private LocalDate transactionDate;

	@Field(value = "corporate_action", targetType = FieldType.STRING)
	private CorporateActionType corporateActionType;

	@Field("corporate_actions")
	List<CorporateActionEntity> corporateActions = new ArrayList<>();


	/**
	 * The client's own idempotency key, when it supplies one — authoritative, and permanent.
	 *
	 * <p>Unique and sparse: a key may be absent, but a key that is present identifies exactly one
	 * transaction forever. Unlike {@link #tradeFingerprint} this carries no time window, because the
	 * client has asserted that two submissions bearing this key are the same submission.
	 */
	@Indexed(unique = true, sparse = true)
	@Field("idempotency_key")
	private String idempotencyKey;

	/**
	 * The derived fallback when no client key is supplied — a hash of the trade's identifying fields.
	 *
	 * <p><b>Deliberately not unique.</b> Two genuinely identical trades on one day are legitimate and
	 * must both be accepted, so a fingerprint on its own can never reject anything. It is the
	 * fingerprint <em>within a short window of</em> {@link #submittedAt} that marks a retry.
	 */
	@Indexed(sparse = true)
	@Field("trade_fingerprint")
	private String tradeFingerprint;

	/**
	 * When the trade was submitted, as distinct from the date it was dated.
	 *
	 * <p>Stored explicitly rather than read from {@code auditMetadata.createdAt}: this class carries
	 * {@code @AllArgsConstructor}, which is the ADR-27 trap — Lombok's {@code @ConstructorProperties}
	 * makes the mapper bypass field initialisers, so audit metadata can arrive null and never be
	 * filled. A window that silently anchors on null would disable duplicate detection.
	 */
	@Field("submitted_at")
	private LocalDateTime submittedAt;

	@Indexed(unique = true, sparse = true)
	@Field("source_temp_transaction_id")
	private String sourceTempTransactionId;

	@Field("status")
	private TransactionStatus status;

	@Field("asset_request")
	private AssetRequest assetRequest;

	@Field("audit_metadata")
	@Setter(value = AccessLevel.NONE)
	private AuditMetadata auditMetadata = new AuditMetadata();

}
