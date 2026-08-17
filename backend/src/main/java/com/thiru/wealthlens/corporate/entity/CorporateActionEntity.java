package com.thiru.wealthlens.corporate.entity;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@NoArgsConstructor
@Document(value = "corporate_action")
public class CorporateActionEntity implements AuditableEntity {
    @MongoId
    private String id;
    @Field("stock_code")
    private String stockCode;
    @Field("stock_name")
    private String stockName;
    @Field("to_stock_code")
    private String toStockCode;
    @Field("to_stock_name")
    private String toStockName;
    @Field(name = "type", targetType = FieldType.STRING)
    private CorporateActionType type;
    @Field(name = "asset_type", targetType = FieldType.STRING)
    private AssetType assetType;
    @Field("description")
    private String description;
    @Field("record_date")
    private LocalDate recordDate;
    @Field("ex_date")
    private LocalDate exDate;
    @Field("priority")
    private int priority;
    @Field("action_price")
    private String actionPrice;
    @Field("ratio")
    private String ratio;
    @Field("date")
    private LocalDate date;
    @Field("demerger_detail")
    private DemergerDetail demergerDetail;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
