package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.dto.AssetEntityProtoType;
import com.thiru.wealthlens.shared.dto.TransactionEntityProtoType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
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
public class AssetRequest implements AssetEntityProtoType, TransactionEntityProtoType {
    private String tempTransactionId; // To keep track of temp transactions
    private String email;
    private String stockCode;
    private String stockName;
    private String exchangeName;
    private BrokerName brokerName;
    private AssetType assetType;
    private Double price;
    private Double quantity;
    private TransactionType transactionType;
    private AccountType accountType = AccountType.SELF;
    private String accountHolder;
    private String orderId;
    private double brokerCharges;
    private double miscCharges;
    private String comment;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate maturityDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    private LocalDate transactionDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_TIME_FORMAT)
    @JsonIgnore
    private LocalDateTime orderExecutionDateTime;

    // remove this as we use orderTimeQuantity
    private Long orderExecutionTimestamp;
    private List<OrderTimeQuantity> orderTimeQuantities = new ArrayList<>();
    private String timezoneId = TLocalDate.TIME_ZONE_IST;

    @Override
    @JsonIgnore
    public TransactionEntity asTransaction() {

        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setId(tempTransactionId);
        transactionEntity.setEmail(email);
        transactionEntity.setStockCode(stockCode);
        transactionEntity.setStockName(stockName);
        transactionEntity.setExchangeName(exchangeName);
        transactionEntity.setBrokerName(brokerName);
        transactionEntity.setPrice(price);
        transactionEntity.setQuantity(quantity);
        transactionEntity.setTotalValue(price * quantity);
        transactionEntity.setBrokerCharges(brokerCharges);
        transactionEntity.setMiscCharges(miscCharges);
        transactionEntity.setComment(comment);
        transactionEntity.setAssetType(assetType);
        transactionEntity.setMaturityDate(maturityDate);
        transactionEntity.setOrderId(orderId);
        transactionEntity.setOrderExecutionTime(orderExecutionDateTime());
        transactionEntity.setTimezoneId(timezoneId);
        transactionEntity.setAccountType(accountType);
        transactionEntity.setAccountHolder(accountHolder);
        transactionEntity.setTransactionType(transactionType);
        transactionEntity.setTransactionDate(transactionDate);
        if (tempTransactionId != null) {
            transactionEntity.setSourceTempTransactionId(tempTransactionId);
        }
        return transactionEntity;
    }

    @Override
    @JsonIgnore
    public AssetEntity asAsset() {
        AssetEntity assetEntity = new AssetEntity();

        assetEntity.setStockCode(stockCode);
        assetEntity.setEmail(email);
        assetEntity.setExchangeName(exchangeName);
        assetEntity.setStockName(stockName);
        assetEntity.setBrokerName(brokerName);
        assetEntity.setPrice(price);
        assetEntity.setQuantity(quantity);
        assetEntity.setBrokerCharges(brokerCharges);
        assetEntity.setMiscCharges(miscCharges);
        assetEntity.setAssetType(assetType);
        assetEntity.setMaturityDate(maturityDate);
        assetEntity.setOrderId(orderId);
        assetEntity.setTransactionDate(transactionDate);
        assetEntity.setAccountType(accountType);
        assetEntity.setAccountHolder(accountHolder);
        assetEntity.setOrderTimeQuantities(orderTimeQuantities);
        assetEntity.setTransactionType(transactionType);
        assetEntity.setTimezoneId(timezoneId);
        assetEntity.setComment(comment);

        return assetEntity;
    }

    public LocalDateTime orderExecutionDateTime() {
        validateOrderExecutionDateTime();

        if (orderExecutionDateTime != null) {
            return TLocalDateTime.atUtc(orderExecutionDateTime, timezoneId);
        }
        if (orderExecutionTimestamp != null) {
            return TLocalDateTime.atUtc(orderExecutionTimestamp, timezoneId);
        }
        return null;
    }

    private void validateOrderExecutionDateTime() {
        if (orderExecutionDateTime != null || orderExecutionTimestamp != null) {
            if (timezoneId == null) {
                throw new IllegalArgumentException("Timezone is required if orderExecutionDateTime is provided");
            }
        }
    }

}
