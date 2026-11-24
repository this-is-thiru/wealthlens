package com.thiru.wealthlens.portfolio.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TransactionEntity {

	@JsonIgnore
	@Id
	private String id;

	@Column(name = "email", nullable = false)
	@Convert(converter = SearchableColumn.class)
	private String email;

	@Column(name = "stock_code")
	private String stockCode;

	@Column(name = "stock_name")
	private String stockName;

	@Column(name = "exchange_name")
	private String exchangeName;

	@Column(name = "broker_name")
	@Enumerated(EnumType.STRING)
	private BrokerName brokerName;

	@Column(name = "price")
	private double price;

	@Column(name = "quantity")
	private Double quantity;

	@Column(name = "total_value")
	private double totalValue;

	/** Delivery for everything recorded before Chunk 10b; there was no segment concept until then. */
	@Column(name = "segment")
	@Enumerated(EnumType.STRING)
	private TradeSegment segment = TradeSegment.DELIVERY;

	@Column(name = "broker_charges")
	private double brokerCharges;

	@Column(name = "misc_charges")
	private double miscCharges;

	@Column(name = "comment")
	private String comment;

	@Column(name = "asset_type")
	@Enumerated(EnumType.STRING)
	private AssetType assetType;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	@Column(name = "maturity_date")
	private LocalDate maturityDate;

	@Column(name = "order_id")
	private String orderId;

	@Column(name = "order_execution_time")
	private LocalDateTime orderExecutionTime;

	@Column(name = "timezone_id")
	private String timezoneId = TLocalDate.TIME_ZONE_IST;

	@Column(name = "account_type")
	@Enumerated(EnumType.STRING)
	private AccountType accountType;

	@Column(name = "account_holder")
	@Convert(converter = SearchableColumn.class)
	private String accountHolder;

	@Column(name = "transaction_type")
	@Enumerated(EnumType.STRING)
	private TransactionType transactionType;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
	@Column(name = "transaction_date")
	private LocalDate transactionDate;

	@Column(name = "corporate_action")
	@Enumerated(EnumType.STRING)
	private CorporateActionType corporateActionType;

	@Column(name = "corporate_actions_json")
	@Convert(converter = CorporateActionListConverter.class)
	List<CorporateActionEntity> corporateActions = new ArrayList<>();

	/**
	 * The client's own idempotency key, when it supplies one — authoritative, and permanent.
	 *
	 * <p>Unique and sparse: a key may be absent, but a key that is present identifies exactly one
	 * transaction forever. Unlike {@link #tradeFingerprint} this carries no time window, because the
	 * client has asserted that two submissions bearing this key are the same submission.
	 */
	@Column(name = "idempotency_key")
	private String idempotencyKey;

	/**
	 * The derived fallback when no client key is supplied — a hash of the trade's identifying fields.
	 *
	 * <p><b>Deliberately not unique.</b> Two genuinely identical trades on one day are legitimate and
	 * must both be accepted, so a fingerprint on its own can never reject anything. It is the
	 * fingerprint <em>within a short window of</em> {@link #submittedAt} that marks a retry.
	 */
	@Column(name = "trade_fingerprint")
	private String tradeFingerprint;

	/**
	 * When the trade was submitted, as distinct from the date it was dated.
	 *
	 * <p>Stored explicitly rather than read from {@code auditMetadata.createdAt}: this class carries
	 * {@code @AllArgsConstructor}; Hibernate never uses it to materialise a row (it always uses the
	 * no-arg constructor and property access), but a window that anchored on audit metadata would
	 * still be wrong the moment anything else constructs this entity directly.
	 */
	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;

	@Column(name = "source_temp_transaction_id")
	private String sourceTempTransactionId;

	@Column(name = "status")
	@Enumerated(EnumType.STRING)
	private TransactionStatus status;

	@Column(name = "asset_request_json")
	@Convert(converter = AssetRequestConverter.class)
	private AssetRequest assetRequest;

	/**
	 * The flow that received the trade: on a {@code TEMPORARY} one so its redrive uses the same flow (W-270), and on
	 * every stored trade so the lot shape it was given can be told — V1 merges a same-day buy, V2 does not — when
	 * {@code assets} is rebuilt (W-283a1, D-580). Absent on a bonus allotment, which no flow received, and on a
	 * transaction stored before W-283a1 (a temporary before W-270 is redriven through V1, D-555 C2a).
	 */
	@Column(name = "trade_flow")
	@Enumerated(EnumType.STRING)
	private TradeFlow tradeFlow;

	@Embedded
	@Setter(value = AccessLevel.NONE)
	private AuditColumns auditMetadata = new AuditColumns();

	@PrePersist
	void assignIdIfAbsent() {
		if (this.id == null) {
			this.id = UUID.randomUUID().toString();
		}
	}
}
