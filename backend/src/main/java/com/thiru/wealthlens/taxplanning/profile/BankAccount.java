package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A bank account: its kind routes its interest; only a masked number is held ({@code DR 15} F4).
 */
@Entity
@Table(name = "tax_bank_accounts")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class BankAccount implements CreatedWithId, ProfileScoped {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "ifsc")
    private String ifsc;

    @Column(name = "masked_number")
    private String maskedNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private AccountKind kind;

    @Column(name = "nominated_for_refund")
    private boolean nominatedForRefund;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    /** Whether this instance is not yet stored (W-329b): never stored, never read back. */
    @Transient
    @JsonIgnore
    private boolean newlyCreated;

    @Override
    public boolean isNew() {
        return newlyCreated || id == null;
    }
}
