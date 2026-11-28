package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A person in a taxpayer's family, as facts only ({@code DR 15} F2, departing from it by CLAUDE.md §4, D-440): no field or method here
 * states a legal date or a status derived from one; the tax side's rows read {@code dateOfBirth}.
 */
@Entity
@Table(name = "tax_family_members")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class FamilyMember implements CreatedWithId, ProfileScoped {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship")
    private FamilySnapshot.Relationship relationship;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "dependant")
    private boolean dependant;

    @Enumerated(EnumType.STRING)
    @Column(name = "disability")
    private DisabilitySeverity disability;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "date_of_death")
    private LocalDate dateOfDeath;

    @Column(name = "multiple_birth_after_first_child")
    private boolean multipleBirthAfterFirstChild;

    @Column(name = "resident_in_india")
    private boolean residentInIndia;

    @Column(name = "pan")
    private String pan;

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
