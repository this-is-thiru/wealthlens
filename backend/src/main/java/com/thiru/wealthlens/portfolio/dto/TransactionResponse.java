package com.thiru.wealthlens.portfolio.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.charges.ChargeNote;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.model.AuditMetadataDto;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class TransactionResponse {

    /**
     * The trade's own id. Exposed because it is the key a caller needs to line a row up against
     * its charges, and because {@code TransactionEntity.id} is {@code @JsonIgnore} — so until now
     * a response could not be joined to anything at all.
     */
    private String transactionId;

    private String email;
    private String stockCode;
    private String stockName;
    private String exchangeName;
    private BrokerName brokerName;
    private AssetType assetType;
    private double price;
    private Double quantity;
    private double totalValue;
    private TransactionType transactionType;
    private String orderId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT)
    private LocalDateTime orderExecutionTime;
    private String timezoneId = TLocalDate.TIME_ZONE_IST;
    private String accountType;
    private String accountHolder;
    private double brokerCharges;
    private double miscCharges;
    private String comment;
    private CorporateActionType corporateActionType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate maturityDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate transactionDate;

    List<CorporateActionDto> corporateActions = new ArrayList<>();
    private String sourceTempTransactionId;
    private AuditMetadataDto auditMetadata;

    /**
     * What the engine charged for this trade, line by line.
     *
     * <p>Null when no charge was ever recorded — a V1 trade, or one made before the engine was
     * switched on, which ADR-32 settles will never be re-driven. A zero would read as "charged
     * nothing", which is a different and sometimes correct fact.
     */
    private ChargeNote charges;
}
