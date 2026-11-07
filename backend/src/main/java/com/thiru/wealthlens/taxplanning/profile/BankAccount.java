package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A bank account: its kind routes its interest; only a masked number is held ({@code DR 15} F4).
 */
@Data
@NoArgsConstructor
@Document("tax_bank_accounts")
public class BankAccount implements AuditableEntity, ProfileScoped {

    @MongoId
    private String id;

    @Indexed
    @Field("profile_id")
    private String profileId;

    @Field("bank_name")
    private String bankName;

    @Field("ifsc")
    private String ifsc;

    @Field("masked_number")
    private String maskedNumber;

    @Field(name = "kind", targetType = FieldType.STRING)
    private AccountKind kind;

    @Field("nominated_for_refund")
    private boolean nominatedForRefund;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
