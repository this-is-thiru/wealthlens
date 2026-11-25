package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * An employer, one per TAN within a profile, referenced by id across years ({@code DR 15} F3; D-046 B5).
 */
@Entity
@Table(name = "tax_employers")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class Employer implements CreatedWithId, ProfileScoped {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "name")
    private String name;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "tan")
    private String tan;

    /** The TAN's keyed digest, which keeps a TAN unique within a profile now that the TAN is stored encrypted (D-447 C1a). */
    @Column(name = "tan_digest")
    private String tanDigest;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "pan")
    private String pan;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private EmployerType type;

    @Column(name = "address")
    private String address;

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
