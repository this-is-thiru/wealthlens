package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
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
 * A landlord: data about someone who is not the user ({@code DR 15} F3; retention {@code DR 14} O5, W-239).
 */
@Entity
@Table(name = "tax_landlords")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class Landlord implements CreatedWithId, ProfileScoped {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "name")
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "pan")
    private String pan;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_to_taxpayer")
    private FamilySnapshot.Relationship relationshipToTaxpayer;

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
