package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
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
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A taxpayer: who a computation is for, with their PAN and the facts the Acts turn on ({@code DR 15} F1). An account owns several
 * (D-046 B2), so a PAN is unique and an email is not. {@code id} is an opaque UUID, the key deep paths carry instead of an email
 * ({@code DR 14} O1.2).
 */
@Entity
@Table(name = "tax_profiles")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class TaxpayerProfile implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "email")
    private String email;

    @Column(name = "pan")
    private String pan;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private AssesseeKind kind;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "residential_status")
    private ResidentialStatus residentialStatus;

    /** Whether the account's portfolio is this person's: its trades belong to one profile, or they would be taxed twice (D-442). */
    @Column(name = "holds_portfolio")
    private boolean holdsPortfolio;

    @Enumerated(EnumType.STRING)
    @Column(name = "disability")
    private DisabilitySeverity disability;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Column(name = "date_of_death")
    private LocalDate dateOfDeath;

    /**
     * When the user asked for this profile to be erased, in UTC (W-235). From then nothing reads or processes it; it is erased once
     * the hold the law requires has passed ({@code PrivacySettings#erasureHold}, W-237d).
     */
    @Column(name = "erasure_requested_at")
    private LocalDateTime erasureRequestedAt;

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
