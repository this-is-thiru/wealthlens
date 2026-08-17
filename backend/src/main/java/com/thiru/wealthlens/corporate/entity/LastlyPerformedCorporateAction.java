package com.thiru.wealthlens.corporate.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(value = "lastly_performed_corporate_action")
public class LastlyPerformedCorporateAction implements AuditableEntity {
    @JsonIgnore
    @MongoId
    private String id;

    private String email;

    @Field("stock_code")
    private String stockCode;

    @Field("asset_type")
    private AssetType assetType;

    @Field("action_type")
    private CorporateActionType actionType;

    @Field("action_date")
    private LocalDate actionDate;

    @Field("broker_name")
    private BrokerName brokerName;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private final AuditMetadata auditMetadata = new AuditMetadata();
}
